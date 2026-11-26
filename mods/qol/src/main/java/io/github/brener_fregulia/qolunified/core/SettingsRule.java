package io.github.brener_fregulia.qolunified.core;

import java.util.List;
import java.util.Optional;

/**
 * A feature's rule across several of its settings (zoom-v1.md §0, bounds correction). Single-value checks stay in
 * {@link SettingDescriptor#accepts}; a rule only rejects combinations, such as a minimum above a maximum. It is pure:
 * no Minecraft types, no side effects.
 */
@FunctionalInterface
public interface SettingsRule {
    /** No cross-setting constraint. */
    SettingsRule NONE = (settings, changedKey) -> Optional.empty();

    /**
     * @param settings   the candidate settings, every single value already accepted
     * @param changedKey the setting being edited, or {@code null} when checking stored settings as a whole
     * @return why the combination is rejected, phrased for the edited setting
     */
    Optional<Violation> check(SettingsSnapshot settings, String changedKey);

    /** A translation key and its arguments, shown next to the rejected field. */
    record Violation(String messageKey, List<Object> args) {
        public Violation {
            args = List.copyOf(args);
        }
    }
}
