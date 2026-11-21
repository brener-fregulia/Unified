package io.github.brener_fregulia.qolunified.ui.screen;

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

/** A scrolling list of rows, each with up to two lines of text and right-aligned widgets. */
final class RowList extends ContainerObjectSelectionList<RowList.Row> {
    static final int ROW_HEIGHT = 36;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int HEADER = 0xFFFFD866;

    RowList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, ROW_HEIGHT);
    }

    void setRows(List<Row> rows) {
        replaceEntries(rows);
        setScrollAmount(0);
    }

    @Override
    public int getRowWidth() {
        return Math.max(160, Math.min(560, getWidth() - 24));
    }

    static Row header(Font font, Component text) {
        return new Row(font, text, null, HEADER, List.of());
    }

    static Row row(Font font, Component title, Component detail, List<AbstractWidget> widgets) {
        return new Row(font, title, detail, TEXT, widgets);
    }

    static final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Font font;
        private final Component title;
        private final Component detail;
        private final int titleColor;
        private final List<AbstractWidget> widgets;

        private Row(Font font, Component title, Component detail, int titleColor, List<AbstractWidget> widgets) {
            this.font = font;
            this.title = title;
            this.detail = detail;
            this.titleColor = titleColor;
            this.widgets = List.copyOf(widgets);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            int right = getContentRight();
            for (int i = widgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = widgets.get(i);
                right -= widget.getWidth();
                widget.setX(right);
                widget.setY(getContentY() + (getContentHeight() - widget.getHeight()) / 2);
                widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
                right -= 4;
            }
            int textWidth = Math.max(20, right - getContentX() - 4);
            int y = detail == null ? getContentYMiddle() - font.lineHeight / 2 : getContentY() + 4;
            graphics.text(font, truncate(title, textWidth), getContentX(), y, titleColor);
            if (detail != null) {
                graphics.text(font, truncate(detail, textWidth), getContentX(), y + font.lineHeight + 3, MUTED);
            }
        }

        private String truncate(Component text, int width) {
            String plain = text.getString();
            if (font.width(plain) <= width) {
                return plain;
            }
            return font.plainSubstrByWidth(plain, Math.max(0, width - font.width("..."))) + "...";
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }

        List<AbstractWidget> widgets() {
            return new ArrayList<>(widgets);
        }
    }
}
