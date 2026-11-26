package unified.optimizations

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import unified.optimizations.core.OptimizationFeatures
import unified.optimizations.core.Optimizations
import unified.settings.api.PanelArea
import unified.settings.api.SettingsWorkspace
import unified.settings.api.WorkspaceFeature
import unified.settings.api.WorkspaceHost
import unified.settings.ui.FeatureSwitch

/**
 * Unified: Optimizations' part of the shared settings workspace (contract 1.1): its implemented features under
 * Network and Performance, each with the shared ON/OFF switch. A change is saved first and applied only once saved
 * (live); a failed save leaves the feature as it was and says so on the footer.
 */
class OptimizationsWorkspace : SettingsWorkspace {
    private lateinit var host: WorkspaceHost

    override fun attach(host: WorkspaceHost) {
        this.host = host
    }

    override fun features(): List<WorkspaceFeature> = OptimizationFeatures.ALL
        .sortedBy { it.category.ordinal }
        .map { f ->
            WorkspaceFeature(f.category.id, f.category.nameKey, f.id, f.nameKey, I18n.get(f.nameKey),
                I18n.get(f.descriptionKey))
        }

    override fun panel(featureId: String, area: PanelArea) {
        val feature = OptimizationFeatures.byId(featureId) ?: return
        val font = area.font
        val conflicts = Optimizations.conflictsOf(featureId)
        val toggle = FeatureSwitch(Component.translatable(feature.nameKey), Optimizations.isEnabled(featureId)) { on ->
            val error = Optimizations.setEnabled(featureId, on)
            if (error == null) {
                host.setMessage(Component.empty(), false)
            } else {
                host.setMessage(Component.translatable(SAVE_FAILED, Component.translatable(feature.nameKey)), true)
            }
            host.requestRebuild()
        }
        toggle.x = area.x + area.width - FeatureSwitch.WIDTH - 2
        toggle.y = area.y
        if (conflicts.isNotEmpty()) {
            toggle.setTooltip(Tooltip.create(Component.translatable(CONFLICT, conflicts.joinToString())))
        }
        area.add(toggle)
        val textWidth = area.width - FeatureSwitch.WIDTH - 12
        val name = StringWidget(area.x + 4, area.y + 1, textWidth, font.lineHeight,
            Component.translatable(feature.nameKey).copy().withStyle(ChatFormatting.BOLD), font)
        area.add(name)
        val status = when {
            conflicts.isNotEmpty() -> Component.translatable(CONFLICT, conflicts.joinToString()).withColor(WARNING)
            Optimizations.isActive(featureId) -> Component.translatable(ACTIVE).withColor(ACTIVE_COLOR)
            else -> Component.translatable(INACTIVE).withColor(MUTED)
        }
        val statusWidget = StringWidget(area.x + 4, area.y + 11, textWidth, font.lineHeight, status, font)
        statusWidget.setMaxWidth(textWidth)
        area.add(statusWidget)
        val description = MultiLineTextWidget(Component.translatable(feature.descriptionKey).withColor(MUTED), font)
            .setMaxWidth(area.width - 16)
        description.x = area.x + 8
        description.y = area.y + 32
        area.add(description)
    }

    companion object {
        const val ACTIVE: String = "unified_optimizations.status.active"
        const val INACTIVE: String = "unified_optimizations.status.inactive"
        const val CONFLICT: String = "unified_optimizations.status.conflict"
        const val SAVE_FAILED: String = "unified_optimizations.message.save_failed"
        private const val WARNING = 0xFFFFC040.toInt()
        private const val ACTIVE_COLOR = 0xFFB8D8B8.toInt()
        private const val MUTED = 0xFFA8A8A8.toInt()
    }
}
