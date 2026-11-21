package io.github.brener_fregulia.qolunified.core;

import io.github.brener_fregulia.qolunified.core.config.ConfigurationService;
import io.github.brener_fregulia.qolunified.core.config.FeatureChoice;
import io.github.brener_fregulia.qolunified.core.config.LoadStatus;
import io.github.brener_fregulia.qolunified.core.config.SaveResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects the registry, configuration and lifecycle, and offers the Save → Apply operation for the
 * future settings screen (TX-5, TX-6). Every operation must run on the client thread (LC-7) and fails
 * fast otherwise.
 */
public final class CoreRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");

    private final FeatureRegistry registry;
    private final ConfigurationService configuration;
    private final FeatureLifecycle lifecycle;
    private final Thread clientThread;

    private CoreRuntime(FeatureRegistry registry, ConfigurationService configuration, FeatureLifecycle lifecycle,
            Thread clientThread) {
        this.registry = registry;
        this.configuration = configuration;
        this.lifecycle = lifecycle;
        this.clientThread = clientThread;
    }

    /** Loads configuration, initializes the lifecycle from it and starts enabled features. */
    public static CoreRuntime start(FeatureRegistry registry, ConfigurationService configuration, Thread clientThread) {
        Objects.requireNonNull(clientThread, "clientThread");
        requireThread(clientThread);
        LoadStatus status = configuration.load();
        var desired = new LinkedHashMap<String, DesiredFeature>();
        configuration.choices().forEach((id, choice) -> desired.put(id, choice.toDesired()));
        var lifecycle = new FeatureLifecycle(registry, desired);
        ApplyReport report = lifecycle.startup();
        LOGGER.info("Configuration {}{}; {} of {} features active", status,
                status.readOnly() ? " (read-only)" : "",
                report.outcomes().stream().filter(o -> o.state() == FeatureState.ACTIVE).count(),
                registry.features().size());
        return new CoreRuntime(registry, configuration, lifecycle, clientThread);
    }

    /**
     * Validates and saves a draft, then applies runtime changes only if the save persisted. A failed
     * runtime transition does not undo the save (TX-7).
     *
     * @throws IllegalArgumentException if the draft is invalid; nothing is saved or applied
     */
    public CommitResult saveAndApply(Map<String, FeatureChoice> draft) {
        requireThread(clientThread);
        SaveResult saved = configuration.save(draft);
        if (!saved.persisted()) {
            return new CommitResult(saved, new ApplyReport(java.util.List.of()));
        }
        var changes = new LinkedHashMap<String, DesiredFeature>();
        draft.forEach((id, choice) -> changes.put(id, configuration.choices().get(id).toDesired()));
        return new CommitResult(saved, lifecycle.apply(changes));
    }

    /** Explicit player retry of a failed disable (LC-5). */
    public TransitionOutcome retryDisable(String featureId) {
        requireThread(clientThread);
        return lifecycle.retryDisable(featureId);
    }

    public FeatureRegistry registry() {
        return registry;
    }

    /** Read access to lifecycle state; mutations go through this class. */
    public FeatureLifecycle lifecycle() {
        return lifecycle;
    }

    public Map<String, FeatureChoice> choices() {
        return configuration.choices();
    }

    public LoadStatus configurationStatus() {
        return configuration.status();
    }

    public boolean configurationReadOnly() {
        return configuration.readOnly();
    }

    private static void requireThread(Thread owner) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Core operations must run on the client thread (current: "
                    + Thread.currentThread().getName() + ")");
        }
    }

    /** Save outcome and runtime outcomes of one commit. {@code report} is empty when nothing was persisted. */
    public record CommitResult(SaveResult save, ApplyReport report) {
    }
}
