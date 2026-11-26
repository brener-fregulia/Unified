package unified.settings.ui

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import unified.settings.api.PanelArea
import unified.settings.api.SettingsWorkspace
import unified.settings.api.WorkspaceHost
import unified.settings.registry.ModuleEntry
import unified.settings.registry.ProviderRegistry

/**
 * The shared settings workspace (docs/architecture/unified-settings-integration.md §20). Its frame is the approved
 * Unified: QoL workspace (settings-ux-v1.md), moved here: the search across the top, the sidebar of modules, categories
 * and features (a drawer below 400 GUI px), the selected feature's panel and the footer with Back, or Retry / Undo /
 * Back after a failed save. Each module draws its own panel through its [SettingsWorkspace] and saves through its own
 * configuration; this screen never touches a module's settings. Navigation and search state live in fields, so they
 * survive a module's tool screen shown over this one.
 *
 * @param preselectModule with [preselectFeature], the feature to select first (a contextual entry point)
 */
class UnifiedSettingsScreen @JvmOverloads constructor(
    private val parent: Screen?,
    private val preselectModule: String? = null,
    private val preselectFeature: String? = null,
) : Screen(Component.translatable(WorkspaceKeys.TITLE)) {

    private val logger = LoggerFactory.getLogger("unified_settings")
    private val sessions = LinkedHashMap<String, Session>()
    private var navigation: WorkspaceNavigation? = null
    private var message: Component = Component.empty()
    private var messageIsError = false
    private var drawerOpen = false
    private var pendingReveal: String? = null
    private var navScroll = 0.0
    private var rebuildRequested = false
    private var messageWidget: StringWidget? = null
    private var nav: NavList? = null
    private var building = false

    /** One installed module in this settings session. */
    private class Session(val entry: ModuleEntry, val workspace: SettingsWorkspace?, var failed: Boolean)

    /** The sidebar state; for development game tests. */
    fun navigation(): WorkspaceNavigation = navigation ?: createNavigation().also { navigation = it }

    /** The workspace session of [moduleId], or null when it offers none or failed; modules use it to find theirs. */
    fun workspace(moduleId: String): SettingsWorkspace? = sessions[moduleId]?.takeUnless { it.failed }?.workspace

    /** The sidebar rows currently shown; for development game tests. */
    fun navRows(): List<WorkspaceNavigation.Row> = nav?.buttons()?.map { it.row } ?: emptyList()

    /** The footer message; for development game tests. */
    fun message(): Component = message

    // ---- Modules ----

    private fun createNavigation(): WorkspaceNavigation {
        val modules = ProviderRegistry.modules().map { entry ->
            val provider = entry.provider
            val nameKey = provider?.let { safe(entry.modId, "name") { it.nameKey } } ?: entry.modId
            var workspace: SettingsWorkspace? = null
            var failed = provider == null
            if (provider != null) {
                workspace = safe(entry.modId, "workspace") {
                    provider.workspace()?.also { it.attach(Host(entry.modId)) }
                }
                if (workspace == null && hasFailed(entry.modId)) failed = true
            }
            val features = workspace?.let { w ->
                safe(entry.modId, "features") { w.features() } ?: run { failed = true; emptyList() }
            } ?: emptyList()
            sessions[entry.modId] = Session(entry, workspace, failed)
            WorkspaceNavigation.Module(entry.modId, nameKey, features.map { f ->
                WorkspaceNavigation.Feature(f.categoryId, f.categoryNameKey, f.id, f.nameKey, f.name, f.description,
                    f.settings.map { WorkspaceNavigation.Setting(it.key, it.nameKey, it.label, it.optionLabels) })
            }, failed)
        }
        return WorkspaceNavigation(modules, preselectModule, preselectFeature)
    }

    private val failures = mutableSetOf<String>()

    private fun hasFailed(modId: String) = modId in failures

    /** Runs a provider call; a throw is logged with the module id and isolates that module. */
    private fun <T> safe(modId: String, what: String, call: () -> T): T? = try {
        call()
    } catch (t: Throwable) {
        logger.error("Settings provider of {} failed ({})", modId, what, t)
        failures += modId
        sessions[modId]?.failed = true
        null
    }

    private fun live(): List<Pair<String, SettingsWorkspace>> =
        sessions.values.filter { !it.failed && it.workspace != null }.map { it.entry.modId to it.workspace!! }

    // ---- Layout (settings-ux-v1.md §19) ----

    /** Breakpoints: below 400 a drawer, then 120, 150 and 170 GUI px. */
    private fun sidebarWidth(): Int = when {
        width >= 960 -> 170
        width >= 640 -> 150
        width >= 400 -> 120
        else -> 0
    }

    /** Left edge: on wide windows the sidebar and the bounded panel are centered together. */
    private fun workspaceLeft(): Int {
        val used = sidebarWidth() + GAP + PANEL_MAX
        return if (width - 2 * GAP > used) (width - used) / 2 else GAP
    }

    private fun workspaceWidth(): Int = minOf(sidebarWidth() + GAP + PANEL_MAX, width - 2 * GAP)

    private fun footerHeight(): Int = FOOTER + if (message.string.isEmpty()) 0 else MESSAGE_LINE

    /** Below 400 GUI px the sidebar becomes a rail with a drawer (settings-ux-v1.md §10). */
    fun compact(): Boolean = sidebarWidth() == 0

    override fun init() {
        building = true
        try {
            build()
        } finally {
            building = false
        }
    }

    private fun build() {
        val navigation = navigation()
        for ((modId, workspace) in live()) {
            safe(modId, "opened") { workspace.opened() }
        }
        val titleWidth = minOf(font.width(title), width - 2 * GAP)
        addRenderableWidget(StringWidget((width - titleWidth) / 2, 5, titleWidth, font.lineHeight, title, font))
        // The search box spans the workspace (sidebar + panel) so it lines up with what it filters.
        val searchX = if (compact()) GAP + 24 else workspaceLeft()
        val searchWidth = if (compact()) width - searchX - GAP else workspaceWidth()
        val search = EditBox(font, searchX, 18, searchWidth, 20, Component.translatable(WorkspaceKeys.SEARCH_HINT))
        search.setHint(Component.translatable(WorkspaceKeys.SEARCH_HINT))
        search.value = navigation.query()
        search.setResponder { value ->
            navigation.setQuery(value)
            if (compact() && navigation.searching()) drawerOpen = true
            rebuildKeepingSearchFocus()
        }
        addRenderableWidget(search)

        val showNav = navigation.hasFeatures() || navigation.showsModules()
        if (!showNav) {
            val only = navigation.modules().singleOrNull()
            val text = when {
                only == null -> Component.translatable(WorkspaceKeys.EMPTY)
                only.failed -> Component.translatable(WorkspaceKeys.PROVIDER_FAILED, Component.translatable(only.nameKey))
                else -> Component.translatable(WorkspaceKeys.NO_SETTINGS, Component.translatable(only.nameKey))
            }
            val empty = MultiLineTextWidget(text, font).setMaxWidth(minOf(320, width - 2 * GAP)).setCentered(true)
            empty.x = (width - empty.width) / 2
            empty.y = TOP + 20
            addRenderableWidget(empty)
            nav = null
        } else {
            val bodyHeight = maxOf(40, height - TOP - footerHeight())
            if (compact()) {
                addRenderableWidget(Button.builder(Component.literal("☰")) {
                    drawerOpen = !drawerOpen
                    rebuildWidgets()
                }.bounds(GAP, 18, 20, 20).tooltip(Tooltip.create(Component.translatable(WorkspaceKeys.NAV_DRAWER)))
                    .build())
            }
            if (!compact() || drawerOpen) {
                val navWidth = if (compact()) minOf(200, width - 2 * GAP) else sidebarWidth()
                val list = NavList(minecraft, navWidth, bodyHeight, TOP)
                list.x = if (compact()) GAP else workspaceLeft()
                list.setRows(font, navigation.rows(::flagged), ::navLabel, ::onNav)
                list.setScrollAmount(navScroll)
                list.revealSelected()
                addRenderableWidget(list)
                nav = list
                if (!navigation.hasResults()) {
                    addRenderableWidget(StringWidget(GAP + 4, TOP + 4, navWidth - 8, font.lineHeight,
                        Component.translatable(WorkspaceKeys.NO_MATCHES), font))
                }
            } else {
                nav = null
            }
            if (!compact() || !drawerOpen) panel(bodyHeight)
        }

        val left = if (compact()) GAP else workspaceLeft()
        val span = if (compact()) width - 2 * GAP else workspaceWidth()
        val messageLine = StringWidget(left, height - footerHeight() + 3, span, font.lineHeight, message, font)
        messageLine.setMaxWidth(span)
        addRenderableWidget(messageLine)
        messageWidget = messageLine
        setMessage(message, messageIsError)
        val footerY = height - 24
        val failedSave = live().firstOrNull { (modId, w) -> safe(modId, "saveFailed") { w.saveFailed() } == true }
        if (failedSave != null) {
            // A failed save: try the same change again, or drop it and show what is stored.
            val (modId, workspace) = failedSave
            val w = minOf(110, (span - 2 * GAP) / 3)
            val x = left + (span - 3 * w - 2 * GAP) / 2
            addRenderableWidget(Button.builder(Component.translatable(WorkspaceKeys.RETRY_SAVE)) {
                safe(modId, "retrySave") { workspace.retrySave() }
                rebuildRequested = true
            }.bounds(x, footerY, w, 20).build())
            addRenderableWidget(Button.builder(Component.translatable(WorkspaceKeys.UNDO_CHANGE)) {
                safe(modId, "undoChange") { workspace.undoChange() }
                rebuildWidgets()
            }.bounds(x + w + GAP, footerY, w, 20).build())
            addRenderableWidget(Button.builder(Component.translatable(WorkspaceKeys.BACK)) { onClose() }
                .bounds(x + 2 * (w + GAP), footerY, w, 20).build())
        } else {
            val w = minOf(150, span)
            addRenderableWidget(Button.builder(Component.translatable(WorkspaceKeys.BACK)) { onClose() }
                .bounds(left + (span - w) / 2, footerY, w, 20).build())
        }
    }

    /** The selected feature's panel, drawn by its module. */
    private fun panel(bodyHeight: Int) {
        val navigation = navigation()
        val modId = navigation.selectedModule() ?: return
        val featureId = navigation.selected() ?: return
        val workspace = workspace(modId) ?: return
        val left = if (compact()) GAP else workspaceLeft() + sidebarWidth() + GAP
        val available = if (compact()) width - 2 * GAP else workspaceLeft() + workspaceWidth() - left
        val reveal = pendingReveal
        pendingReveal = null
        val screen = this
        val area = object : PanelArea {
            override val screen: Screen = screen
            override val font = screen.font
            override val x = left
            override val y = TOP
            override val width = minOf(PANEL_MAX, available)
            override val height = bodyHeight
            override val revealSetting = reveal
            override fun <T> add(widget: T): T where T : GuiEventListener, T : Renderable, T : NarratableEntry =
                addRenderableWidget(widget)
        }
        if (safe(modId, "panel") { workspace.panel(featureId, area) } == null) {
            val name = Component.translatable(sessions[modId]?.entry?.provider?.nameKey ?: modId)
            val text = MultiLineTextWidget(Component.translatable(WorkspaceKeys.PANEL_FAILED, name), font)
                .setMaxWidth(area.width - 8)
            text.x = left + 4
            text.y = TOP + 4
            addRenderableWidget(text)
        }
    }

    // ---- Navigation ----

    private fun navLabel(row: WorkspaceNavigation.Row): Component {
        val module = navigation().modules().first { it.id == row.moduleId }
        return when (row.kind) {
            WorkspaceNavigation.Kind.MODULE -> Component.translatable(module.nameKey)
            WorkspaceNavigation.Kind.CATEGORY ->
                Component.translatable(module.features.first { it.categoryId == row.categoryId }.categoryNameKey)
            WorkspaceNavigation.Kind.FEATURE -> Component.translatable(feature(module, row).nameKey)
            WorkspaceNavigation.Kind.SETTING ->
                Component.translatable(feature(module, row).settings.first { it.key == row.settingKey }.nameKey)
            WorkspaceNavigation.Kind.NOTE -> Component.translatable(
                if (row.note == WorkspaceNavigation.Note.FAILED) WorkspaceKeys.NOTE_FAILED
                else WorkspaceKeys.NOTE_NO_FEATURES)
        }
    }

    private fun feature(module: WorkspaceNavigation.Module, row: WorkspaceNavigation.Row) =
        module.features.first { it.id == row.featureId }

    private fun onNav(row: WorkspaceNavigation.Row, expand: Boolean?) {
        val navigation = navigation()
        when (row.kind) {
            WorkspaceNavigation.Kind.MODULE ->
                if (expand == null) navigation.clickModule(row.moduleId)
                else navigation.setModuleExpanded(row.moduleId, expand)
            WorkspaceNavigation.Kind.CATEGORY ->
                if (expand == null) navigation.clickCategory(row.moduleId, row.categoryId!!)
                else navigation.setExpanded(row.moduleId, row.categoryId!!, expand)
            WorkspaceNavigation.Kind.FEATURE -> {
                navigation.select(row.moduleId, row.featureId!!)
                drawerOpen = false
            }
            WorkspaceNavigation.Kind.SETTING -> {
                navigation.select(row.moduleId, row.featureId!!)
                pendingReveal = row.settingKey
                drawerOpen = false
            }
            WorkspaceNavigation.Kind.NOTE -> return
        }
        rebuildWidgets()
    }

    private fun flagged(moduleId: String, featureId: String): Boolean =
        workspace(moduleId)?.let { w -> safe(moduleId, "flagged") { w.flagged(featureId) } } == true

    /** Selects a feature as if chosen in the sidebar; for development game tests and contextual entry points. */
    fun select(moduleId: String, featureId: String) {
        navigation().select(moduleId, featureId)
        if (minecraft != null && !building) rebuildWidgets()
    }

    override fun rebuildWidgets() {
        for ((modId, workspace) in live()) {
            safe(modId, "beforeRebuild") { workspace.beforeRebuild() }
        }
        nav?.let { navScroll = it.scrollAmount() }
        super.rebuildWidgets()
    }

    private fun rebuildKeepingSearchFocus() {
        rebuildWidgets()
        children().firstOrNull { it is EditBox }?.let { box ->
            focused = box
            (box as EditBox).moveCursorToEnd(false)
        }
    }

    override fun tick() {
        super.tick()
        if (rebuildRequested) {
            rebuildRequested = false
            rebuildWidgets()
        }
    }

    private fun setMessage(text: Component, error: Boolean) {
        val widget = messageWidget
        if (widget != null && message.string.isEmpty() != text.string.isEmpty()) {
            rebuildRequested = true // the footer grows or shrinks by the message line
        }
        message = text
        messageIsError = error
        widget?.message = if (error) text.copy().withStyle(ChatFormatting.RED) else text
    }

    /** The services one module's workspace gets; calls from a module never affect another module's state. */
    private inner class Host(private val modId: String) : WorkspaceHost {
        override fun setMessage(text: Component, error: Boolean) = this@UnifiedSettingsScreen.setMessage(text, error)

        override fun messageIsError(): Boolean = messageIsError

        override fun requestRebuild() {
            rebuildRequested = true
        }

        override fun rebuildNow() {
            if (minecraft != null && !building) rebuildWidgets() else rebuildRequested = true
        }

        override fun toString(): String = "WorkspaceHost($modId)"
    }

    /**
     * Back and Escape close the workspace and return to where it was opened. Each module drops what it could not
     * save; valid changes were already saved and applied by their module.
     */
    override fun onClose() {
        for ((modId, workspace) in live()) {
            safe(modId, "closed") { workspace.closed() }
        }
        minecraft.gui.setScreen(parent)
    }

    companion object {
        private const val GAP = 6
        private const val TOP = 44
        /** Footer: the Back row, plus a message line above it while there is a message. */
        private const val FOOTER = 28
        private const val MESSAGE_LINE = 12
        private const val PANEL_MAX = 400
    }
}
