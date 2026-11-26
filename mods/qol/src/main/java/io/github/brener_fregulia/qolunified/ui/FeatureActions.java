package io.github.brener_fregulia.qolunified.ui;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.client.gui.screens.Screen;

/**
 * Screens that a feature offers from its row in the Unified: QoL settings (for example "Open Keymap"). Internal to the
 * mod, not a public API. The settings screen shows an action only while its feature is active.
 */
public final class FeatureActions {
    /**
     * @param labelKey translation key of the button
     * @param open builds the screen from the settings screen (its parent) and the unsaved draft
     */
    public record Action(String labelKey, BiFunction<Screen, SettingsDraft, Screen> open) {
    }

    private static final Map<String, Action> ACTIONS = new ConcurrentHashMap<>();

    private FeatureActions() {
    }

    public static void register(String featureId, Action action) {
        ACTIONS.put(featureId, action);
    }

    public static Optional<Action> find(String featureId) {
        return Optional.ofNullable(ACTIONS.get(featureId));
    }
}
