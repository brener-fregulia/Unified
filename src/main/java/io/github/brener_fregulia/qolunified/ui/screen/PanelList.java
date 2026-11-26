package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.ui.FeatureComparisons;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The scrolling content of the feature panel (settings-ux-v1.md §5): wrapped text, a comparison, action buttons and
 * setting rows, each with its own height. Labels wrap instead of being clipped; controls sit on the right.
 */
final class PanelList extends ContainerObjectSelectionList<PanelList.Row> {
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFFA8A8A8;
    static final int DIMMED = 0xFFC8C8C8;
    static final int WARNING = 0xFFFFC060;
    static final int ERROR = 0xFFFF7070;
    private static final int HIGHLIGHT = 0x40FFD866;
    private static final int GAP = 6;
    private static final int SAMPLE_MIN = 24;
    private int rowWidth;

    PanelList(Minecraft minecraft, int width, int height, int y, int rowWidth) {
        super(minecraft, width, height, y, 24);
        this.rowWidth = rowWidth;
    }

    void setRows(List<Row> rows) {
        clearEntries();
        for (Row row : rows) {
            addEntry(row, row.height);
        }
        setScrollAmount(0);
    }

    /** Total height of the rows, so a short panel does not leave a large empty area. */
    static int contentHeight(List<Row> rows) {
        return rows.stream().mapToInt(r -> r.height).sum() + 8;
    }

    /** Rows start at the panel's left edge (with a small inset) instead of being centered. */
    @Override
    public int getRowLeft() {
        return getX() + 6;
    }

    /** Scrolls so {@code row} is visible (search results reveal their setting). */
    void reveal(Row row) {
        scrollToEntry(row);
    }

    @Override
    public int getRowWidth() {
        return rowWidth;
    }

    @Override
    protected int scrollBarX() {
        return getRight() - 6;
    }

    /** A subtle one-pixel separator between description, comparison, tools and settings. */
    static Row divider() {
        return new Row(7) {
            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
                int y = getContentY() + 3;
                g.fill(getContentX(), y, getContentRight(), y + 1, 0x30FFFFFF);
            }
        };
    }

    // ---- Rows ----

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        final int height;
        /** The main text of the row, for tests and narration-free lookups; may be null. */
        Component text;

        Row(int height) {
            this.height = height;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** Wrapped text, for the description and hints. */
    static Row text(Font font, Component text, int color, int width) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(40, width));
        Row row = new Row(lines.size() * (font.lineHeight + 1) + 4) {
            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
                int y = getContentY() + 1;
                for (FormattedCharSequence line : lines) {
                    g.text(font, line, getContentX(), y, color, true);
                    y += font.lineHeight + 1;
                }
            }
        };
        row.text = text;
        return row;
    }

    /** A labelled control row: the label wraps beside the controls; a second line shows a detail or error. */
    static final class SettingRow extends Row {
        private final Font font;
        private final List<FormattedCharSequence> label;
        private final List<FormattedCharSequence> detail;
        private final int detailColor;
        private final List<AbstractWidget> widgets;
        private final boolean dimmed;
        final String settingKey;
        private long highlightUntil;

        SettingRow(Font font, Component label, Component detail, int detailColor, List<AbstractWidget> widgets,
                boolean dimmed, String settingKey, int width) {
            super(rowHeight(font, label, detail, widgets, width));
            this.font = font;
            this.label = font.split(label, labelWidth(widgets, width));
            this.detail = detail == null ? List.of() : font.split(detail, labelWidth(widgets, width));
            this.text = detail;
            this.detailColor = detailColor;
            this.widgets = List.copyOf(widgets);
            this.dimmed = dimmed;
            this.settingKey = settingKey;
        }

        private static int labelWidth(List<AbstractWidget> widgets, int width) {
            int controls = widgets.stream().mapToInt(w -> w.getWidth() + 4).sum();
            return Math.max(40, width - controls - GAP);
        }

        private static int rowHeight(Font font, Component label, Component detail, List<AbstractWidget> widgets,
                int width) {
            int lines = font.split(label, labelWidth(widgets, width)).size()
                    + (detail == null ? 0 : font.split(detail, labelWidth(widgets, width)).size());
            return Math.max(22, lines * (font.lineHeight + 1) + 6);
        }

        void highlight() {
            highlightUntil = System.currentTimeMillis() + 1500;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            if (System.currentTimeMillis() < highlightUntil) {
                g.fill(getContentX() - 2, getContentY() - 1, getContentRight() + 2, getContentBottom() + 1,
                        HIGHLIGHT);
            }
            int right = getContentRight();
            for (int i = widgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = widgets.get(i);
                right -= widget.getWidth();
                widget.setX(right);
                widget.setY(getContentY() + (getContentHeight() - widget.getHeight()) / 2);
                widget.extractRenderState(g, mouseX, mouseY, delta);
                right -= 4;
            }
            int lines = label.size() + detail.size();
            int y = getContentYMiddle() - (lines * (font.lineHeight + 1)) / 2 + 1;
            for (FormattedCharSequence line : label) {
                g.text(font, line, getContentX(), y, dimmed ? DIMMED : TEXT, true);
                y += font.lineHeight + 1;
            }
            for (FormattedCharSequence line : detail) {
                g.text(font, line, getContentX(), y, detailColor, true);
                y += font.lineHeight + 1;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }

    /** "Vanilla" and "QoL ON" boxes side by side, or stacked when the panel is narrow (settings-ux-v1.md §8). */
    static Row comparison(Font font, FeatureComparisons.Comparison comparison, int width) {
        int box = comparison.width() + 8;
        boolean sideBySide = 2 * box + GAP <= width;
        int labelHeight = font.lineHeight + 3;
        // Samples get the same minimum stage, so short (Food HUD) and tall (Target Info) examples look alike.
        int stage = Math.max(SAMPLE_MIN, comparison.height());
        int one = labelHeight + stage + 2 * 4;
        int height = comparison.available() ? (sideBySide ? one : 2 * one + GAP) + 4 : font.lineHeight + 8;
        return new Row(height) {
            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
                int x = getContentX();
                int y = getContentY();
                // Both boxes share one width, capped so they do not stretch across a wide panel.
                int boxWidth = sideBySide ? Math.min((getContentWidth() - GAP) / 2, box + 40)
                        : Math.min(getContentWidth(), box + 40);
                side(g, x, y, boxWidth, false);
                if (sideBySide) {
                    side(g, x + boxWidth + GAP, y, boxWidth, true);
                } else {
                    side(g, x, y + one + GAP, boxWidth, true);
                }
            }

            private void side(GuiGraphicsExtractor g, int x, int y, int w, boolean qol) {
                if (!comparison.available()) {
                    if (qol) {
                        return;
                    }
                    g.text(font, Component.translatable(UiKeys.COMPARISON_IN_WORLD), x + 4, y + 3, MUTED, true);
                    return;
                }
                g.fill(x, y, x + w, y + one, 0x60000000);
                g.outline(x, y, w, one, qol ? 0xFF55AA55 : 0xFF707070);
                Component title = Component.translatable(qol ? UiKeys.COMPARISON_QOL : UiKeys.COMPARISON_VANILLA);
                g.text(font, title, x + 4, y + 3, qol ? 0xFF8FE08F : MUTED, true);
                int cx = x + (w - comparison.width()) / 2;
                comparison.draw(g, font, cx, y + labelHeight + 4 + (stage - comparison.height()) / 2, qol);
            }
        };
    }

    static List<AbstractWidget> widgets(Row row) {
        return row instanceof SettingRow s ? new ArrayList<>(s.widgets) : List.of();
    }
}
