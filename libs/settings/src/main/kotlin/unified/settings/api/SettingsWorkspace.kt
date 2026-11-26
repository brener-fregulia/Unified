package unified.settings.api

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * One module's part of the shared settings workspace for one settings session (contract 1.1,
 * docs/architecture/unified-settings-integration.md §20). The library owns the frame: title, search, the module /
 * category / feature sidebar, the drawer, the footer with Back (and Retry / Undo after a failed save), and the layout.
 * The module owns everything inside a feature's panel: its switch, description, comparison, tools and setting rows,
 * plus its configuration values, validation, persistence and applying them. The library never reads or writes a
 * module's configuration.
 *
 * Created by [UnifiedSettingsProvider.workspace] once per opened workspace screen and kept while that screen lives,
 * including while a module's tool screen is shown over it. Every call is made on the client thread; a call that throws
 * is logged with the module id and isolates that module only.
 */
interface SettingsWorkspace {
    /** The module's features, in display order. Categories appear in the order their first feature does. */
    fun features(): List<WorkspaceFeature>

    /** Called once, before the first [features] call, with the screen's services for this module. */
    fun attach(host: WorkspaceHost) {}

    /** The screen is being (re)built, e.g. after returning from a tool screen. Commit pending tool edits here. */
    fun opened() {}

    /**
     * Adds the panel of [featureId] (one of this module's features) to the screen inside [area]: the sticky header at
     * [PanelArea.y] and the scrolling content below it.
     */
    fun panel(featureId: String, area: PanelArea)

    /** The screen is about to rebuild its widgets: remember scroll positions or similar view state. */
    fun beforeRebuild() {}

    /** Whether [featureId] has input that is not a valid value yet; the sidebar flags it. */
    fun flagged(featureId: String): Boolean = false

    /** The last change of this module could not be saved: the footer offers Retry and Undo. */
    fun saveFailed(): Boolean = false

    /** Footer Retry after a failed save. */
    fun retrySave() {}

    /** Footer Undo after a failed save: drop the change and show what is stored. */
    fun undoChange() {}

    /** The workspace screen is closing (Back or Escape). Drop anything unsaved. */
    fun closed() {}
}

/** What the screen offers a module's workspace. */
interface WorkspaceHost {
    /** Shows [text] on the footer message line (red when [error]); an empty text hides the line. */
    fun setMessage(text: Component, error: Boolean)

    /** Whether the footer currently shows an error. */
    fun messageIsError(): Boolean

    /** Rebuilds the screen on the next tick, outside the widget event being handled. */
    fun requestRebuild()

    /** Rebuilds the screen now. */
    fun rebuildNow()
}

/** Where and how a module adds one feature's panel. */
interface PanelArea {
    val screen: Screen
    val font: Font
    val x: Int
    val y: Int
    val width: Int

    /** Height available below [y] for the header and the scrolling content. */
    val height: Int

    /** A setting the sidebar search led to, to scroll into view and highlight; null otherwise. */
    val revealSetting: String?

    /** Adds a widget to the screen, after the sidebar in focus order. */
    fun <T> add(widget: T): T where T : GuiEventListener, T : Renderable, T : NarratableEntry
}

/**
 * One feature as the shared sidebar and search see it.
 *
 * @param categoryId the module's category id; unique within the module
 * @param name translated name, for search
 * @param description translated description, for search
 */
data class WorkspaceFeature(
    val categoryId: String,
    val categoryNameKey: String,
    val id: String,
    val nameKey: String,
    val name: String,
    val description: String,
    val settings: List<WorkspaceSetting> = emptyList(),
)

/**
 * One setting as search sees it.
 *
 * @param label translated name
 * @param optionLabels translated labels of its values, for search
 */
data class WorkspaceSetting(
    val key: String,
    val nameKey: String,
    val label: String,
    val optionLabels: List<String> = emptyList(),
)
