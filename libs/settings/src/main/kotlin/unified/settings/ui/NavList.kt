package unified.settings.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

/**
 * The sidebar tree (moved from Unified: QoL, settings-ux-v1.md §4.1, §9.1): optional module rows, category rows with
 * an arrow and feature count, indented feature rows with a selection marker, and indented setting rows while
 * searching. Every row is a 20 px button, so mouse, Enter/Space, Tab and narration work like vanilla; Left/Right
 * collapse and expand a module or category.
 */
class NavList(minecraft: Minecraft, width: Int, height: Int, y: Int) :
    ContainerObjectSelectionList<NavList.Entry>(minecraft, width, height, y, ROW) {

    /** What a row does when used; [expand] is null for Enter/click, true/false for Right/Left. */
    fun interface Action {
        fun run(row: WorkspaceNavigation.Row, expand: Boolean?)
    }

    fun setRows(font: Font, rows: List<WorkspaceNavigation.Row>, label: (WorkspaceNavigation.Row) -> Component,
                action: Action) {
        clearEntries()
        for (row in rows) {
            val text = label(row)
            val button = RowButton(font, row, text, action)
            // Narrow sidebars truncate names; the full name stays available on hover.
            button.setTooltip(Tooltip.create(text))
            if (row.kind == WorkspaceNavigation.Kind.NOTE) {
                button.active = false
            }
            addEntry(Entry(button))
        }
    }

    /** Keeps the selected feature in view after the tree changed (expand, search, return from a tool). */
    fun revealSelected() {
        children().firstOrNull { it.button.row.selected }?.let { scrollToEntry(it) }
    }

    /** The row buttons; for development game tests. */
    fun buttons(): List<RowButton> = children().map { it.button }

    override fun getRowWidth(): Int = width - 8

    override fun scrollBarX(): Int = right - 6

    class Entry(val button: RowButton) : ContainerObjectSelectionList.Entry<Entry>() {
        override fun extractContent(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
            button.x = contentX
            button.y = contentY
            button.width = contentWidth
            button.extractRenderState(g, mouseX, mouseY, delta)
        }

        override fun children(): List<GuiEventListener> = listOf(button)

        override fun narratables(): List<NarratableEntry> = listOf(button)
    }

    /** One sidebar row. Its message is the plain translated name, so screens and tests can find it by key. */
    class RowButton(private val font: Font, val row: WorkspaceNavigation.Row, label: Component,
                    private val action: Action) : AbstractButton(0, 0, 100, ROW - 2, label) {

        override fun onPress(input: InputWithModifiers) {
            action.run(row, null)
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            val expandable = row.kind == WorkspaceNavigation.Kind.CATEGORY || row.kind == WorkspaceNavigation.Kind.MODULE
            if (expandable && (event.key() == InputConstants.KEY_RIGHT || event.key() == InputConstants.KEY_LEFT)) {
                action.run(row, event.key() == InputConstants.KEY_RIGHT)
                return true
            }
            return super.keyPressed(event)
        }

        override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val x = x
            val y = y
            val w = width
            val h = height
            if (row.selected) {
                g.fill(x, y, x + w, y + h, SELECTED_BG)
                g.fill(x, y, x + 2, y + h, CATEGORY) // accent bar on the selected feature
            } else if (isHoveredOrFocused && active) {
                g.fill(x, y, x + w, y + h, 0x28FFFFFF)
            }
            if (isFocused) {
                g.outline(x, y, w, h, -0x1)
            }
            val ty = y + (h - font.lineHeight) / 2 + 1
            val indent = row.depth * INDENT
            when (row.kind) {
                WorkspaceNavigation.Kind.MODULE, WorkspaceNavigation.Kind.CATEGORY -> {
                    val module = row.kind == WorkspaceNavigation.Kind.MODULE
                    val color = if (module) TEXT else CATEGORY
                    val arrow = if (row.expanded) "▼" else "▶"
                    val count = row.count.toString()
                    val countWidth = font.width(count)
                    g.text(font, arrow, x + 4 + indent, ty, color, true)
                    val textX = x + 14 + indent
                    val showCount = !module && w - (textX - x) - countWidth - 6 > 30
                    val textWidth = w - (textX - x) - if (showCount) countWidth + 8 else 3
                    g.text(font, fit(message.string, textWidth), textX, ty, color, true)
                    if (showCount) {
                        g.text(font, count, x + w - countWidth - 4, ty, MUTED, true)
                    }
                }
                WorkspaceNavigation.Kind.FEATURE -> {
                    // Indented under the category name; the accent bar and background mark the selection.
                    val textX = x + 18 + indent
                    val flag = if (row.flagged) " !" else ""
                    val flagWidth = font.width(flag)
                    val name = fit(message.string, w - (textX - x) - flagWidth - 3)
                    g.text(font, name, textX, ty, if (row.selected) TEXT else 0xFFE0E0E0.toInt(), true)
                    if (row.flagged) {
                        g.text(font, flag, textX + font.width(name), ty, FLAG, true)
                    }
                }
                WorkspaceNavigation.Kind.SETTING -> {
                    val textX = x + 26 + indent
                    g.text(font, fit("· " + message.string, w - (textX - x) - 3), textX, ty, MUTED, true)
                }
                WorkspaceNavigation.Kind.NOTE -> {
                    val textX = x + 14 + indent
                    g.text(font, fit(message.string, w - (textX - x) - 3), textX, ty, MUTED, true)
                }
            }
        }

        private fun fit(text: String, width: Int): String {
            if (font.width(text) <= width) return text
            return font.plainSubstrByWidth(text, maxOf(0, width - font.width("..."))) + "..."
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            val state: Component = when (row.kind) {
                WorkspaceNavigation.Kind.MODULE, WorkspaceNavigation.Kind.CATEGORY -> Component.translatable(
                    if (row.expanded) WorkspaceKeys.NAV_EXPANDED else WorkspaceKeys.NAV_COLLAPSED, row.count)
                WorkspaceNavigation.Kind.FEATURE ->
                    if (row.selected) Component.translatable(WorkspaceKeys.NAV_SELECTED) else Component.empty()
                else -> Component.empty()
            }
            output.add(NarratedElementType.TITLE, Component.empty().append(message).append(" ").append(state))
            if (active) {
                output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"))
            }
        }
    }

    companion object {
        const val ROW: Int = 20
        private const val INDENT = 8
        private const val SELECTED_BG = 0x50FFFFFF
        private const val TEXT = -0x1
        private val CATEGORY = 0xFFFFD866.toInt()
        private val MUTED = 0xFFA8A8A8.toInt()
        private val FLAG = 0xFFFF7070.toInt()
    }
}
