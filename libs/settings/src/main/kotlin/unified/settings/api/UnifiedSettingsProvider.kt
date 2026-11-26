package unified.settings.api

import net.minecraft.client.gui.screens.Screen

/**
 * What one Unified mod contributes to Unified Settings (docs/architecture/unified-settings-integration.md §6, §19).
 * Each mod declares exactly one implementation under the Fabric entrypoint [ENTRYPOINT].
 *
 * Contract 1.1 (1.0 plus [workspace]). Compatibility rules (verified by the M2.1.0 spike, §18): a mod's `depends` lower bound is the contract
 * version it was compiled against; within a major version members are only added, and every added member has a
 * default, so older providers keep working; a breaking change is a new major version.
 *
 * Implementable from Java: properties are getters (`getModuleId()`), and members with defaults are real default
 * methods.
 */
interface UnifiedSettingsProvider {
    /** The owning mod's Fabric id, e.g. `qol_unified`. */
    val moduleId: String

    /** Translation key of the module's display name, owned by the module, e.g. "Unified: QoL". */
    val nameKey: String

    /** Position among modules; see [ModuleOrder]. Lower comes first. */
    val order: Int

    /** Top-level navigation the module offers, for the shared screen. Empty when it has nothing to configure. */
    fun categories(): List<SettingsCategory> = emptyList()

    /**
     * The module's own settings screen, opened over [parent]; null when it has nothing to configure yet. Since 1.1 the
     * shared workspace uses [workspace] instead; this stays for 1.0 providers and is no longer called for a module
     * that offers a workspace.
     */
    fun openSettings(parent: Screen?): Screen?

    /**
     * Since 1.1: the module's part of a new shared workspace session, or null when it has no configurable features
     * (it is then listed with "no configurable features yet"). Called once per opened workspace screen.
     */
    fun workspace(): SettingsWorkspace? = null

    /** Optional abilities for later contract versions, e.g. [Capabilities.SEARCH]. Unknown names are ignored. */
    fun capabilities(): Set<String> = emptySet()

    companion object {
        /** The Fabric entrypoint name under which mods declare their provider. */
        const val ENTRYPOINT: String = "unified-settings"
    }
}

/** A category a module shows in navigation. */
data class SettingsCategory(val id: String, val nameKey: String, val featureCount: Int)

/** The approved module order (Q-US5). */
object ModuleOrder {
    const val QOL: Int = 10
    const val OPTIMIZATIONS: Int = 20
    const val GRAPHICS: Int = 30
}

/** Capability names reserved for later contract versions; none is required in 1.0. */
object Capabilities {
    const val SEARCH: String = "search"
    const val EMBEDDED_PANEL: String = "embedded_panel"
}

/** The contract version this library implements. */
object ContractVersion {
    const val CURRENT: String = "1.1.0"
}
