package io.github.brener_fregulia.qolunified.ui;

import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingsRule;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.core.TransitionOutcome;
import io.github.brener_fregulia.qolunified.core.config.FeatureChoice;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The settings screen's draft (TX-1–TX-4) and the commit entry point. Edits change only the draft;
 * {@link #commit()} delegates validation, saving and applying to {@link CoreRuntime}.
 */
public final class SettingsDraft {
    private final CoreRuntime core;
    private Map<String, FeatureChoice> original;
    private final Map<String, FeatureChoice> draft = new LinkedHashMap<>();
    private final Map<String, String> invalidInputs = new LinkedHashMap<>();

    public SettingsDraft(CoreRuntime core) {
        this.core = core;
        rebase();
    }

    /** Outcome of pressing Done. */
    public record CommitOutcome(Kind kind, List<TransitionOutcome> failures) {
        public enum Kind {
            /** Nothing changed; nothing saved or applied. The screen may close. */
            NO_CHANGE,
            /** Saved and applied without failures. The screen may close. */
            APPLIED,
            /** Saved; some runtime transitions failed. Not a save failure (TX-7). */
            APPLIED_WITH_FAILURES,
            /** The draft has invalid input; nothing saved. */
            INVALID,
            /** Configuration is read-only; nothing saved (TX-9). */
            READ_ONLY,
            /** Saving failed; nothing applied; the draft is kept (TX-5). */
            SAVE_FAILED
        }

        public boolean closesScreen() {
            return kind == Kind.NO_CHANGE || kind == Kind.APPLIED;
        }
    }

    public CoreRuntime core() {
        return core;
    }

    public FeatureChoice choice(String featureId) {
        FeatureChoice choice = draft.get(featureId);
        if (choice == null) {
            throw new IllegalArgumentException("Unknown feature: " + featureId);
        }
        return choice;
    }

    /** False when the configuration is read-only; edits could never be saved. */
    public boolean editable() {
        return !core.configurationReadOnly();
    }

    /** Whether the enable control may be used. An UNKNOWN feature can only be retried (LC-8). */
    public boolean canToggle(String featureId) {
        return editable() && core.lifecycle().state(featureId) != FeatureState.UNKNOWN;
    }

    /** Records an explicit enable choice, which also overrides a conflict default (CMP-5). */
    public void setEnabled(String featureId, boolean enabled) {
        if (enabled && !canToggle(featureId)) {
            throw new IllegalStateException("Feature " + featureId + " cannot be enabled now");
        }
        requireEditable();
        draft.put(featureId, choice(featureId).withEnabled(enabled));
    }

    /** Sets a boolean or enum value. Returns an error key if the value is not allowed. */
    public Optional<String> setValue(String featureId, String key, Object value) {
        requireEditable();
        SettingDescriptor setting = setting(featureId, key);
        if (!setting.accepts(value)) {
            invalidInputs.put(fieldId(featureId, key), String.valueOf(value));
            return Optional.of(UiKeys.ERROR_INVALID_VALUE);
        }
        FeatureChoice choice = choice(featureId);
        SettingsSnapshot candidate = choice.settings().with(key, value);
        Optional<SettingsRule.Violation> violation = choice.settings().descriptor().rule().check(candidate, key);
        if (violation.isPresent()) {
            invalidInputs.put(fieldId(featureId, key), String.valueOf(value));
            return Optional.of(violation.get().messageKey());
        }
        invalidInputs.remove(fieldId(featureId, key));
        draft.put(featureId, choice.withSettings(candidate));
        return Optional.empty();
    }

    /**
     * Checks a single-value-valid {@code value} against the feature's cross-setting rule without changing the draft,
     * so a field can explain a rejected combination before anything is written (zoom-v1.md §0).
     */
    public Optional<SettingsRule.Violation> ruleViolation(String featureId, String key, Object value) {
        FeatureChoice choice = choice(featureId);
        return choice.settings().descriptor().rule().check(choice.settings().with(key, value), key);
    }

    /** Parses integer input. Invalid text is kept as a field error and blocks Done. */
    public Optional<String> setIntegerText(String featureId, String key, String text) {
        requireEditable();
        if (!(setting(featureId, key) instanceof SettingDescriptor.IntegerSetting integer)) {
            throw new IllegalArgumentException("Setting " + key + " is not an integer");
        }
        int value;
        try {
            value = Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            invalidInputs.put(fieldId(featureId, key), text);
            return Optional.of(UiKeys.ERROR_NOT_A_NUMBER);
        }
        if (value < integer.min() || value > integer.max()) {
            invalidInputs.put(fieldId(featureId, key), text);
            return Optional.of(UiKeys.ERROR_OUT_OF_RANGE);
        }
        return setValue(featureId, key, value);
    }

    public boolean hasInvalidInput(String featureId, String key) {
        return invalidInputs.containsKey(fieldId(featureId, key));
    }

    /** The rejected text of a field, if any, so it can be shown again. */
    public Optional<String> invalidInput(String featureId, String key) {
        return Optional.ofNullable(invalidInputs.get(fieldId(featureId, key)));
    }

    public boolean valid() {
        return invalidInputs.isEmpty();
    }

    public boolean changed() {
        return !draft.equals(original);
    }

    /** Validates, then saves and applies through the Core (TX-3–TX-8). */
    public CommitOutcome commit() {
        if (!valid()) {
            return new CommitOutcome(CommitOutcome.Kind.INVALID, List.of());
        }
        if (core.configurationReadOnly()) {
            return new CommitOutcome(CommitOutcome.Kind.READ_ONLY, List.of());
        }
        if (!changed()) {
            return new CommitOutcome(CommitOutcome.Kind.NO_CHANGE, List.of());
        }
        var changes = new LinkedHashMap<String, FeatureChoice>();
        draft.forEach((id, choice) -> {
            if (!choice.equals(original.get(id))) {
                changes.put(id, choice);
            }
        });
        CoreRuntime.CommitResult result = core.saveAndApply(changes);
        return switch (result.save()) {
            case FAILED -> new CommitOutcome(CommitOutcome.Kind.SAVE_FAILED, List.of());
            case READ_ONLY -> new CommitOutcome(CommitOutcome.Kind.READ_ONLY, List.of());
            case NO_CHANGE, SAVED -> {
                // The file now holds the draft; later Done presses compare against it and never re-apply.
                rebase();
                List<TransitionOutcome> failures =
                        result.report().outcomes().stream().filter(TransitionOutcome::failed).toList();
                yield new CommitOutcome(failures.isEmpty() ? CommitOutcome.Kind.APPLIED
                        : CommitOutcome.Kind.APPLIED_WITH_FAILURES, failures);
            }
        };
    }

    /**
     * Drops uncommitted edits and invalid inputs, returning to the saved choices. Used after a failed save, so the
     * controls show what is actually stored and active (settings-ux-v1.md §18).
     */
    public void revert() {
        rebase();
    }

    /** Explicit player retry for an UNKNOWN feature (LC-5). Does not touch the draft or the file. */
    public TransitionOutcome retryDisable(String featureId) {
        return core.retryDisable(featureId);
    }

    public boolean restartRequired(String featureId) {
        return core.lifecycle().pendingRestart(featureId);
    }

    private void rebase() {
        original = core.choices();
        draft.clear();
        draft.putAll(original);
        invalidInputs.clear();
    }

    private void requireEditable() {
        if (!editable()) {
            throw new IllegalStateException("Configuration is read-only");
        }
    }

    private SettingDescriptor setting(String featureId, String key) {
        FeatureDescriptor descriptor = core.registry().require(featureId).descriptor();
        return descriptor.setting(key).orElseThrow(() ->
                new IllegalArgumentException("Unknown setting " + featureId + "." + key));
    }

    private static String fieldId(String featureId, String key) {
        return featureId + "." + key;
    }
}
