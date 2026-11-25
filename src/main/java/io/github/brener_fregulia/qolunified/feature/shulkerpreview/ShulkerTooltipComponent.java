package io.github.brener_fregulia.qolunified.feature.shulkerpreview;

import io.github.brener_fregulia.qolunified.feature.shulkerpreview.ShulkerSummary.Kind;
import io.github.brener_fregulia.qolunified.feature.shulkerpreview.ShulkerSummary.Slot;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the shulker preview inside the vanilla tooltip (shulker-preview-v1.md §6.2, §6.3). The compact layout lists
 * the predominant kind, the other kinds and a summary line; the full layout is the exact 9×3 grid. The box's dye
 * color is a thin accent line; everything else uses vanilla sprites, item rendering and text.
 */
public final class ShulkerTooltipComponent implements ClientTooltipComponent {
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
    private static final int SLOT_SIZE = 18;
    private static final int ROW = 18;
    /** Spacing of the compact "other kinds" row: a little wider than a slot so counts do not touch. */
    private static final int KIND_STEP = 20;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAAAAAA;
    private static final int HINT = 0xFF707070;

    private final ShulkerTooltip data;

    public ShulkerTooltipComponent(ShulkerTooltip data) {
        this.data = data;
    }

    private ShulkerSummary<ItemStack> summary() {
        return data.summary();
    }

    // ---- Measuring ----

    @Override
    public int getHeight(Font font) {
        if (data.full()) {
            return 3 + 3 * SLOT_SIZE + 4;
        }
        int height = 3 + ROW + 2; // accent + predominant row
        if (!summary().listedOthers().isEmpty()) {
            height += ROW + 1;
        }
        height += 10; // summary line
        if (data.shiftHint()) {
            height += 10;
        }
        return height + 2;
    }

    @Override
    public int getWidth(Font font) {
        if (data.full()) {
            return 9 * SLOT_SIZE;
        }
        Kind<ItemStack> main = summary().predominant();
        int width = 20 + Math.max(font.width(kindName(main)), font.width(itemsText(main.total())));
        int others = summary().listedOthers().size() * KIND_STEP;
        if (summary().hiddenKinds() > 0) {
            others += 2 + font.width(moreText());
        }
        width = Math.max(width, others);
        width = Math.max(width, font.width(summaryText()));
        if (data.shiftHint()) {
            width = Math.max(width, font.width(Component.translatable(ShulkerPreviewTexts.SHIFT_HINT)));
        }
        return width;
    }

    // ---- Drawing ----

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
        // Subtle accent in the box's color (Q-SP5).
        g.fill(x, y + 1, x + getWidth(font), y + 2, ARGB.color(0xB0, data.accent()));
        int top = y + 3;
        if (data.full()) {
            full(g, font, x, top);
        } else {
            compact(g, font, x, top);
        }
    }

    private void compact(GuiGraphicsExtractor g, Font font, int x, int y) {
        Kind<ItemStack> main = summary().predominant();
        g.item(main.representative(), x, y + 1);
        g.text(font, kindName(main), x + 20, y, TEXT, true);
        g.text(font, itemsText(main.total()), x + 20, y + 9, DIM, true);
        y += ROW + 2;
        List<Kind<ItemStack>> others = summary().listedOthers();
        if (!others.isEmpty()) {
            int ox = x;
            for (Kind<ItemStack> kind : others) {
                g.item(kind.representative(), ox, y);
                g.itemDecorations(font, kind.representative(), ox, y, ShulkerSummary.shortCount(kind.total()));
                ox += KIND_STEP;
            }
            if (summary().hiddenKinds() > 0) {
                g.text(font, moreText(), ox + 2, y + 5, DIM, true);
            }
            y += ROW + 1;
        }
        g.text(font, summaryText(), x, y, DIM, true);
        y += 10;
        if (data.shiftHint()) {
            g.text(font, Component.translatable(ShulkerPreviewTexts.SHIFT_HINT), x, y, HINT, true);
        }
    }

    private void full(GuiGraphicsExtractor g, Font font, int x, int y) {
        List<Slot<ItemStack>> slots = summary().slots();
        for (int i = 0; i < ShulkerSummary.SLOTS; i++) {
            int sx = x + (i % 9) * SLOT_SIZE;
            int sy = y + (i / 9) * SLOT_SIZE;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, sx, sy, SLOT_SIZE, SLOT_SIZE);
            Slot<ItemStack> slot = slots.get(i);
            if (!slot.empty()) {
                // The original stack, as a container draws it: count, durability bar and glint.
                g.item(slot.stack(), sx + 1, sy + 1);
                g.itemDecorations(font, slot.stack(), sx + 1, sy + 1);
            }
        }
    }

    // ---- Texts ----

    private static Component kindName(Kind<ItemStack> kind) {
        // Grouping ignores components, so the kind is named after the item, not a stack's custom name.
        return kind.representative().getItem().getName(kind.representative().getItem().getDefaultInstance());
    }

    private static Component itemsText(long total) {
        return Component.translatable(ShulkerPreviewTexts.ITEMS, total);
    }

    private Component moreText() {
        return Component.translatable(ShulkerPreviewTexts.MORE_KINDS, summary().hiddenKinds());
    }

    private Component summaryText() {
        return Component.translatable(ShulkerPreviewTexts.SUMMARY, summary().usedSlots(), ShulkerSummary.SLOTS,
                summary().totalItems(), summary().kinds().size());
    }
}
