package io.github.brener_fregulia.qolunified.core;

import io.github.brener_fregulia.qolunified.core.FailureRecord.Operation;
import io.github.brener_fregulia.qolunified.core.TransitionOutcome.Kind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies desired state to features and tracks their effective state (M0 §4).
 *
 * <p>Lifecycle operations run only on {@link #startup()}, on a changed desired state passed to
 * {@link #apply(Map)}, or on an explicit {@link #retryDisable(String)}. Nothing is retried
 * automatically (LC-13). Must be called from the client thread (LC-7).
 */
public final class FeatureLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");

    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean started;

    public FeatureLifecycle(FeatureRegistry registry, Map<String, DesiredFeature> initial) {
        this(registry, initial, Clock.systemUTC());
    }

    FeatureLifecycle(FeatureRegistry registry, Map<String, DesiredFeature> initial, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        for (String id : initial.keySet()) {
            registry.require(id);
        }
        for (Feature feature : registry.features()) {
            FeatureDescriptor descriptor = feature.descriptor();
            DesiredFeature desired = initial.getOrDefault(descriptor.id(), DesiredFeature.defaults(descriptor));
            requireMatching(descriptor, desired);
            entries.put(descriptor.id(), new Entry(feature, desired));
        }
    }

    /** Enables every feature whose desired state is enabled (LC-1). May be called once. */
    public ApplyReport startup() {
        if (started) {
            throw new IllegalStateException("Lifecycle already started");
        }
        started = true;
        var outcomes = new ArrayList<TransitionOutcome>();
        for (Entry entry : entries.values()) {
            if (entry.desiredEnabled) {
                outcomes.add(enable(entry));
            }
        }
        return new ApplyReport(outcomes);
    }

    /**
     * Applies desired changes that have already been saved (TX-6). Only features whose desired state
     * differs from the current one cause lifecycle calls. All changes are validated before any is applied.
     */
    public ApplyReport apply(Map<String, DesiredFeature> changes) {
        if (!started) {
            throw new IllegalStateException("Lifecycle not started");
        }
        for (var change : changes.entrySet()) {
            requireMatching(require(change.getKey()).feature.descriptor(), change.getValue());
        }
        var toDisable = new ArrayList<Entry>();
        var toEnable = new ArrayList<Entry>();
        var outcomes = new ArrayList<TransitionOutcome>();
        for (Entry entry : entries.values()) {
            DesiredFeature change = changes.get(entry.id());
            if (change == null) {
                continue;
            }
            entry.applySettings(change.settings());
            if (change.enabled() == entry.desiredEnabled) {
                if (entry.settingsPendingRestart()) {
                    outcomes.add(entry.outcome(Kind.PENDING_RESTART));
                }
                continue;
            }
            entry.desiredEnabled = change.enabled();
            if (entry.toggleMode() == ApplyMode.RESTART_REQUIRED) {
                if (entry.pendingRestart()) {
                    outcomes.add(entry.outcome(Kind.PENDING_RESTART));
                }
            } else if (!change.enabled()) {
                if (entry.state != FeatureState.INACTIVE) {
                    toDisable.add(entry);
                }
            } else if (entry.state == FeatureState.INACTIVE) {
                toEnable.add(entry);
            } else if (entry.state == FeatureState.UNKNOWN) {
                LOGGER.warn("Feature {} cannot be enabled while its state is unknown; disable it or restart", entry.id());
                outcomes.add(entry.outcome(Kind.REJECTED_UNKNOWN));
            }
        }
        for (Entry entry : toDisable) {
            outcomes.add(disable(entry, entry.state == FeatureState.UNKNOWN));
        }
        for (Entry entry : toEnable) {
            outcomes.add(enable(entry));
        }
        return new ApplyReport(outcomes);
    }

    /** Explicit player retry of {@code disable()} for a feature in {@link FeatureState#UNKNOWN}. */
    public TransitionOutcome retryDisable(String id) {
        Entry entry = require(id);
        if (entry.state != FeatureState.UNKNOWN) {
            throw new IllegalStateException("Feature " + id + " is not in UNKNOWN state");
        }
        return disable(entry, true);
    }

    public FeatureState state(String id) {
        return require(id).state;
    }

    public Optional<FailureRecord> failure(String id) {
        return Optional.ofNullable(require(id).failure);
    }

    public boolean desiredEnabled(String id) {
        return require(id).desiredEnabled;
    }

    public SettingsSnapshot desiredSettings(String id) {
        return require(id).desiredSettings;
    }

    public SettingsSnapshot effectiveSettings(String id) {
        return require(id).effectiveSettings;
    }

    /** Whether a saved restart-required toggle or setting has not taken effect yet. */
    public boolean pendingRestart(String id) {
        return require(id).pendingRestart();
    }

    private TransitionOutcome enable(Entry entry) {
        try {
            entry.feature.enable(entry.context);
            entry.state = FeatureState.ACTIVE;
            entry.failure = null;
            return entry.outcome(Kind.ENABLED);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("Feature {} failed to enable", entry.id(), e);
            LOGGER.warn("Feature {}: rolling back failed enable", entry.id());
            try {
                entry.feature.disable();
                entry.state = FeatureState.INACTIVE;
                entry.failure = record(Operation.ENABLE, false, e);
            } catch (RuntimeException | LinkageError rollback) {
                LOGGER.error("Feature {} failed to roll back; it may be partly active", entry.id(), rollback);
                entry.state = FeatureState.UNKNOWN;
                entry.failure = record(Operation.ENABLE, true, e);
            }
            return entry.outcome(Kind.ENABLE_FAILED);
        }
    }

    private TransitionOutcome disable(Entry entry, boolean retry) {
        try {
            entry.feature.disable();
            entry.state = FeatureState.INACTIVE;
            entry.failure = null;
            if (retry) {
                LOGGER.info("Feature {} disabled successfully on retry", entry.id());
            }
            return entry.outcome(Kind.DISABLED);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("Feature {} failed to disable; it may still be active", entry.id(), e);
            entry.state = FeatureState.UNKNOWN;
            entry.failure = record(Operation.DISABLE, false, e);
            return entry.outcome(Kind.DISABLE_FAILED);
        }
    }

    private FailureRecord record(Operation operation, boolean rollbackFailed, Throwable error) {
        return new FailureRecord(operation, rollbackFailed, error.getClass().getName(), clock.instant());
    }

    private Entry require(String id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown feature: " + id);
        }
        return entry;
    }

    private static void requireMatching(FeatureDescriptor descriptor, DesiredFeature desired) {
        if (desired.settings().descriptor() != descriptor) {
            throw new IllegalArgumentException("Settings for " + descriptor.id() + " use a different descriptor");
        }
    }

    private static final class Entry {
        final Feature feature;
        final FeatureContext context;
        final boolean startupEnabled;
        boolean desiredEnabled;
        FeatureState state = FeatureState.INACTIVE;
        FailureRecord failure;
        SettingsSnapshot desiredSettings;
        volatile SettingsSnapshot effectiveSettings;

        Entry(Feature feature, DesiredFeature desired) {
            this.feature = feature;
            this.startupEnabled = desired.enabled();
            this.desiredEnabled = desired.enabled();
            this.desiredSettings = desired.settings();
            this.effectiveSettings = desired.settings();
            this.context = () -> effectiveSettings;
        }

        String id() {
            return feature.descriptor().id();
        }

        ApplyMode toggleMode() {
            return feature.descriptor().toggleMode();
        }

        /** LIVE settings take effect now; RESTART_REQUIRED settings keep their effective value (ST-2, ST-3). */
        void applySettings(SettingsSnapshot desired) {
            desiredSettings = desired;
            var effective = new LinkedHashMap<String, Object>(effectiveSettings.values());
            for (SettingDescriptor setting : feature.descriptor().settings()) {
                if (setting.applyMode() == ApplyMode.LIVE) {
                    effective.put(setting.key(), desired.get(setting.key()));
                }
            }
            effectiveSettings = SettingsSnapshot.of(feature.descriptor(), effective);
        }

        boolean settingsPendingRestart() {
            return !desiredSettings.equals(effectiveSettings);
        }

        boolean pendingRestart() {
            return (toggleMode() == ApplyMode.RESTART_REQUIRED && desiredEnabled != startupEnabled)
                    || settingsPendingRestart();
        }

        TransitionOutcome outcome(Kind kind) {
            return new TransitionOutcome(id(), kind, state);
        }
    }
}
