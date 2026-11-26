package unified.optimizations

import net.minecraft.client.gui.screens.Screen
import unified.settings.api.ModuleOrder
import unified.settings.api.SettingsWorkspace
import unified.settings.api.UnifiedSettingsProvider

/**
 * Unified: Optimizations' entry in Unified Settings (contract 1.1): its features in the shared workspace through
 * [OptimizationsWorkspace]. It owns its configuration; the library never reads or writes it.
 */
class OptimizationsSettingsProvider : UnifiedSettingsProvider {
    override val moduleId: String = UnifiedOptimizationsClient.MOD_ID
    override val nameKey: String = "modmenu.nameTranslation.unified_optimizations"
    override val order: Int = ModuleOrder.OPTIMIZATIONS

    override fun openSettings(parent: Screen?): Screen? = null

    override fun workspace(): SettingsWorkspace = OptimizationsWorkspace()
}
