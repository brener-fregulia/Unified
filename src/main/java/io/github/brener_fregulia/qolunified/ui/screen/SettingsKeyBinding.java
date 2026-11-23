package io.github.brener_fregulia.qolunified.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

/**
 * Registers the unbound "Open QoL: Unified settings" key binding. It works in game when no screen is
 * open, and on the title screen. Other screens (including chat) are left alone.
 */
public final class SettingsKeyBinding {
    private SettingsKeyBinding() {
    }

    private static KeyMapping.Category category;

    /** The "QoL: Unified" key-mapping category, shared by every QoL: Unified key mapping. */
    public static KeyMapping.Category category() {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(QolUnifiedClient.MOD_ID, "main"));
        }
        return category;
    }

    public static void register() {
        KeyMapping.Category category = category();
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(UiKeys.KEY_OPEN_SETTINGS,
                InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (key.consumeClick()) {
                if (minecraft.gui.screen() == null) {
                    open(minecraft, null);
                }
            }
        });
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
                    if (!key.isUnbound() && key.matches(event)) {
                        open(minecraft, s);
                    }
                });
            }
        });
    }

    private static void open(Minecraft minecraft, Screen parent) {
        minecraft.gui.setScreen(new SettingsScreen(parent, QolUnifiedClient.core()));
    }
}
