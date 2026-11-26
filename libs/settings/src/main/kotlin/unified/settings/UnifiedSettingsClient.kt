package unified.settings

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import unified.settings.ui.ButtonPlacement
import unified.settings.ui.Rect
import unified.settings.ui.UnifiedSettingsScreen

/**
 * Unified Settings (Library) entrypoint. Fabric loads exactly one copy however many Unified mods nest it (§18), so
 * the key mapping and the Options button below exist once. Initialization only registers them; providers are read
 * when the screen first opens.
 */
class UnifiedSettingsClient : ClientModInitializer {
    override fun onInitializeClient() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "unified"))
        val key = KeyMappingHelper.registerKeyMapping(
            KeyMapping(KEY_OPEN, InputConstants.Type.KEYBOARD, InputConstants.KEY_U, category))
        openKey = key
        ClientTickEvents.END_CLIENT_TICK.register { minecraft ->
            while (key.consumeClick()) {
                if (minecraft.gui.screen() == null) {
                    minecraft.gui.setScreen(UnifiedSettingsScreen(null))
                }
            }
        }
        ScreenEvents.AFTER_INIT.register { _, screen, width, height ->
            if (screen is OptionsScreen) {
                addOptionsButton(screen, width, height)
            }
        }
    }

    /** Adds the single "Unified Settings…" button where it overlaps nothing; replaces it after a resize (found by key). */
    private fun addOptionsButton(screen: Screen, width: Int, height: Int) {
        val widgets = Screens.getWidgets(screen)
        widgets.removeIf(::isOptionsButton)
        val font = Screens.getFont(screen)
        val label = Component.translatable(OPTIONS_BUTTON)
        val buttonWidth = maxOf(90, font.width(label) + 20)
        val done = widgets.firstOrNull { it is Button && it.message == CommonComponents.GUI_DONE }
        val spot = ButtonPlacement.choose(width, height, done?.let(::rect), null, widgets.map(::rect), buttonWidth,
            20) ?: return
        val minecraft = Screens.getMinecraft(screen)
        val button = Button.builder(label) { minecraft.gui.setScreen(UnifiedSettingsScreen(screen)) }
            .bounds(spot.x, spot.y, spot.width, spot.height).build()
        widgets.add(button)
    }

    private fun rect(widget: AbstractWidget) = Rect(widget.x, widget.y, widget.width, widget.height)

    companion object {
        const val MOD_ID: String = "unified_settings"
        const val KEY_OPEN: String = "key.unified_settings.open"
        const val OPTIONS_BUTTON: String = "unified_settings.options_button"

        /** The shared key mapping; for development game tests. */
        @JvmStatic
        var openKey: KeyMapping? = null
            private set

        /** Whether [widget] is the library's Options button; for development game tests. */
        @JvmStatic
        fun isOptionsButton(widget: Any?): Boolean = widget is Button &&
            (widget.message.contents as? TranslatableContents)?.key == OPTIONS_BUTTON
    }
}
