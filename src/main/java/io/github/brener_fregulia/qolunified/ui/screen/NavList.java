package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.ui.SettingsNavigation;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The sidebar tree (settings-ux-v1.md §4.1, §9.1): category rows with an arrow and feature count, indented feature
 * rows with a selection marker, and indented setting rows while searching. Every row is a 20 px button, so mouse,
 * Enter/Space, Tab and narration work like vanilla; Left/Right collapse and expand a category.
 */
final class NavList extends ContainerObjectSelectionList<NavList.Entry> {
    static final int ROW = 20;
    private static final int SELECTED_BG = 0x50FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int CATEGORY = 0xFFFFD866;
    private static final int MUTED = 0xFFA8A8A8;
    private static final int FLAG = 0xFFFF7070;

    /** What a row does when used; {@code expand} is null for Enter/click, true/false for Right/Left. */
    interface Action {
        void run(SettingsNavigation.Row row, Boolean expand);
    }

    NavList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, ROW);
    }

    void setRows(Font font, List<SettingsNavigation.Row> rows, java.util.function.Function<SettingsNavigation.Row,
            Component> label, Action action) {
        clearEntries();
        for (SettingsNavigation.Row row : rows) {
            RowButton button = new RowButton(font, row, label.apply(row), action);
            // Narrow sidebars truncate names; the full name stays available on hover.
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label.apply(row)));
            addEntry(new Entry(button));
        }
    }

    /** Keeps the selected feature in view after the tree changed (expand, search, return from a tool). */
    void revealSelected() {
        children().stream().filter(e -> e.button.row.selected()).findFirst().ifPresent(this::scrollToEntry);
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 8;
    }

    @Override
    protected int scrollBarX() {
        return getRight() - 6;
    }

    static final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        final RowButton button;

        Entry(RowButton button) {
            this.button = button;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            button.setX(getContentX());
            button.setY(getContentY());
            button.setWidth(getContentWidth());
            button.extractRenderState(g, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }
    }

    /** One sidebar row. Its message is the plain translated name, so screens and tests can find it by key. */
    static final class RowButton extends AbstractButton {
        private final Font font;
        final SettingsNavigation.Row row;
        private final Action action;

        RowButton(Font font, SettingsNavigation.Row row, Component label, Action action) {
            super(0, 0, 100, ROW - 2, label);
            this.font = font;
            this.row = row;
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run(row, null);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (row.kind() == SettingsNavigation.Kind.CATEGORY
                    && (event.key() == InputConstants.KEY_RIGHT || event.key() == InputConstants.KEY_LEFT)) {
                action.run(row, event.key() == InputConstants.KEY_RIGHT);
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            if (row.selected()) {
                g.fill(x, y, x + w, y + h, SELECTED_BG);
            } else if (isHoveredOrFocused()) {
                g.fill(x, y, x + w, y + h, 0x28FFFFFF);
            }
            if (isFocused()) {
                g.outline(x, y, w, h, 0xFFFFFFFF);
            }
            int ty = y + (h - font.lineHeight) / 2 + 1;
            switch (row.kind()) {
                case CATEGORY -> {
                    String arrow = row.expanded() ? "▼" : "▶";
                    String count = Integer.toString(row.count());
                    int countWidth = font.width(count);
                    g.text(font, arrow, x + 3, ty, CATEGORY, true);
                    int textX = x + 3 + font.width(arrow) + 3;
                    boolean showCount = w - (textX - x) - countWidth - 6 > 30;
                    int textWidth = w - (textX - x) - (showCount ? countWidth + 8 : 3);
                    g.text(font, fit(getMessage().getString(), textWidth), textX, ty, CATEGORY, true);
                    if (showCount) {
                        g.text(font, count, x + w - countWidth - 4, ty, MUTED, true);
                    }
                }
                case FEATURE -> {
                    int textX = x + 12;
                    String marker = row.selected() ? "»" : "";
                    if (!marker.isEmpty()) {
                        g.text(font, marker, x + 3, ty, TEXT, true);
                    }
                    String flag = row.flagged() ? " !" : "";
                    int flagWidth = font.width(flag);
                    String name = fit(getMessage().getString(), w - (textX - x) - flagWidth - 3);
                    g.text(font, name, textX, ty, row.selected() ? TEXT : 0xFFE0E0E0, true);
                    if (row.flagged()) {
                        g.text(font, flag, textX + font.width(name), ty, FLAG, true);
                    }
                }
                case SETTING -> {
                    int textX = x + 22;
                    g.text(font, fit("· " + getMessage().getString(), w - (textX - x) - 3), textX, ty, MUTED,
                            true);
                }
            }
        }

        private String fit(String text, int width) {
            if (font.width(text) <= width) {
                return text;
            }
            return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            Component state = switch (row.kind()) {
                case CATEGORY -> Component.translatable(row.expanded()
                        ? UiKeys.NAV_EXPANDED : UiKeys.NAV_COLLAPSED, row.count());
                case FEATURE -> row.selected() ? Component.translatable(UiKeys.NAV_SELECTED)
                        : Component.empty();
                case SETTING -> Component.empty();
            };
            output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(" ").append(state));
            output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"));
        }
    }
}
