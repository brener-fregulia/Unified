package unified.settings.ui

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import unified.settings.registry.ModuleEntry
import unified.settings.registry.ProviderRegistry

/**
 * The M2.1.1 integration shell (docs/architecture/unified-settings-integration.md §19): the installed Unified modules
 * in the approved order, each opening its own current settings screen. Not the final workspace; M2.1.2 merges the
 * modules' navigation and panels into one screen.
 */
class UnifiedSettingsScreen(private val parent: Screen?) :
    Screen(Component.translatable("unified_settings.title")) {

    private val logger = LoggerFactory.getLogger("unified_settings")
    private var message: Component = Component.empty()
    private lateinit var messageWidget: MultiLineTextWidget

    override fun init() {
        val modules = ProviderRegistry.modules()
        val buttonWidth = minOf(220, width - 20)
        val x = (width - buttonWidth) / 2
        val titleWidth = minOf(font.width(title), width - 20)
        addRenderableWidget(StringWidget((width - titleWidth) / 2, 12, titleWidth, font.lineHeight, title, font))
        var y = 40
        for (module in modules) {
            addRenderableWidget(Button.builder(label(module)) { open(module) }
                .bounds(x, y, buttonWidth, 20).build())
            y += 24
        }
        if (modules.isEmpty()) {
            message = Component.translatable("unified_settings.no_modules")
        }
        messageWidget = MultiLineTextWidget(message, font).setMaxWidth(buttonWidth).setCentered(true)
        messageWidget.setX(x)
        messageWidget.setY(y + 6)
        addRenderableWidget(messageWidget)
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK) { onClose() }
            .bounds((width - 150) / 2, height - 28, 150, 20).build())
    }

    private fun label(module: ModuleEntry): Component =
        module.provider?.let { Component.translatable(it.nameKey) }
            ?: Component.literal(module.modId).withStyle(ChatFormatting.GRAY)

    /** Opens the module's own screen, or explains why there is none. A failing provider never closes this screen. */
    private fun open(module: ModuleEntry) {
        val provider = module.provider
        if (provider == null) {
            show(Component.translatable("unified_settings.provider_failed", module.modId))
            return
        }
        val screen = try {
            provider.openSettings(this)
        } catch (t: Throwable) {
            logger.error("Settings provider of {} failed to open its screen", module.modId, t)
            show(Component.translatable("unified_settings.provider_failed", Component.translatable(provider.nameKey)))
            return
        }
        if (screen == null) {
            show(Component.translatable("unified_settings.no_settings", Component.translatable(provider.nameKey)))
        } else {
            minecraft.gui.setScreen(screen)
        }
    }

    private fun show(text: Component) {
        message = text
        messageWidget.setMessage(text)
    }

    /** The message currently shown; for development game tests. */
    fun message(): Component = message

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }
}
