package unified.optimizations

import net.minecraft.client.gui.screens.Screen
import unified.settings.api.ModuleOrder
import unified.settings.api.UnifiedSettingsProvider

/**
 * Unified: Optimizations' entry in Unified Settings (contract 1.1). No optimization is configurable yet, so it offers
 * no workspace; the shared workspace lists it with "No configurable features yet" instead of placeholders.
 */
class OptimizationsSettingsProvider : UnifiedSettingsProvider {
    override val moduleId: String = UnifiedOptimizationsClient.MOD_ID
    override val nameKey: String = "modmenu.nameTranslation.unified_optimizations"
    override val order: Int = ModuleOrder.OPTIMIZATIONS

    override fun openSettings(parent: Screen?): Screen? = null
}
