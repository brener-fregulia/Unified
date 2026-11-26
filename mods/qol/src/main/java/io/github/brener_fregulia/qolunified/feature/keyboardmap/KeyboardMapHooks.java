package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.config.FeatureChoice;
import io.github.brener_fregulia.qolunified.ui.FeatureActions;
import io.github.brener_fregulia.qolunified.ui.screen.SettingsKeyBinding;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;

/**
 * The three entry points (keymap-v1.md §9.1). Each one checks {@link KeyboardMapFeature#activeSettings()}, so none
 * opens the screen while the feature is disabled. No Mixin: Fabric's key-mapping, tick and screen events only.
 */
public final class KeyboardMapHooks {
    private static KeyMapping openKey;

    private KeyboardMapHooks() {
    }

    /** EP-1: the unbound "Open Keymap" key mapping. Called once at client start. */
    public static void registerKeyMapping() {
        if (openKey != null) {
            return;
        }
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(KeyboardMapTexts.KEY_OPEN,
                InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), SettingsKeyBinding.category()));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (openKey.consumeClick()) {
                if (KeyboardMapFeature.activeSettings() != null && minecraft.gui.screen() == null) {
                    open(minecraft, null);
                }
            }
        });
    }

    /** EP-2 and EP-3. Installed on the first enable. */
    static void installEntryPoints() {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof ControlsScreen && KeyboardMapFeature.activeSettings() != null) {
                addControlsButton(minecraft, screen);
            }
        });
    }

    /**
     * The settings panel's "Open Keyboard Map" tool, registered at client start so the panel can show it (disabled)
     * before the feature was ever enabled (settings-ux-v1.md Q-SU6). The screen class loads only when opened.
     */
    public static void registerAction() {
        FeatureActions.register(KeyboardMapSettings.ID, new FeatureActions.Action(KeyboardMapTexts.OPEN_TOOL,
                (parent, draft) -> new KeyboardMapScreen(parent,
                        draft.choice(KeyboardMapSettings.ID).settings().getEnum(KeyboardMapSettings.LAYOUT),
                        layout -> draft.setValue(KeyboardMapSettings.ID, KeyboardMapSettings.LAYOUT, layout))));
    }

    /**
     * Adds "Keyboard Map…" as a new row of vanilla's own Controls options list, under Mouse Settings and Key Binds,
     * so it never overlaps a vanilla widget and scrolls with the list.
     */
    private static void addControlsButton(Minecraft minecraft, Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof OptionsList list) {
                list.addSmall(List.of(Button.builder(Component.translatable(KeyboardMapTexts.OPEN),
                        b -> open(minecraft, screen)).build()));
                return;
            }
        }
    }

    /** Opens the screen over {@code parent} (null in game); the layout toolbar saves through the Core. */
    public static void open(Minecraft minecraft, Screen parent) {
        minecraft.gui.setScreen(new KeyboardMapScreen(parent, null, KeyboardMapHooks::saveLayout));
    }

    /** Persists a layout chosen in the toolbar through the Core configuration (keymap-v1.md §0). */
    private static void saveLayout(String layout) {
        CoreRuntime core = QolUnifiedClient.core();
        FeatureChoice choice = core.choices().get(KeyboardMapSettings.ID);
        core.saveAndApply(Map.of(KeyboardMapSettings.ID,
                choice.withSettings(choice.settings().with(KeyboardMapSettings.LAYOUT, layout))));
    }
}
