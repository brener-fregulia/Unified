package io.github.brener_fregulia.qolunified.feature.shulkerpreview;

import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * Mixin SP-1 (shulker-preview-v1.md §6.1, §6.1a, §15): after vanilla's slot decorations, draws the content icon
 * (first occupied slot or predominant item, per {@code slot_icon_source}) at half size in the top-right quadrant
 * and a bundle-style capacity bar on the item-bar row. Nothing is drawn for other items, empty or unknown boxes, or
 * while the feature or setting is off.
 */
public final class ShulkerSlotDecorator {
    /** The bundle's bar colors in Minecraft 26.3 ({@code BundleItem.BAR_COLOR}, {@code FULL_BAR_COLOR}). */
    static final int BAR = ARGB.colorFromFloat(1.0f, 0.44f, 0.53f, 1.0f);
    static final int BAR_FULL = ARGB.colorFromFloat(1.0f, 1.0f, 0.33f, 0.33f);

    private static long decorated;

    private ShulkerSlotDecorator() {
    }

    public static void decorate(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        SettingsSnapshot settings = ShulkerPreviewFeature.activeSettings();
        if (settings == null || !settings.getBoolean(ShulkerPreviewSettings.SLOT_ICON)
                || !ShulkerContents.isShulkerBox(stack)) {
            return;
        }
        ShulkerSummary<ItemStack> summary = ShulkerContents.summary(stack);
        if (summary == null) {
            return;
        }
        // Content icon (first occupied slot or predominant kind): 8×8 in the top-right quadrant, glint kept, no count.
        ItemStack icon = summary.iconStack(settings.getEnum(ShulkerPreviewSettings.SLOT_ICON_SOURCE));
        if (icon != null) {
            g.pose().pushMatrix();
            g.pose().translate(x + 8, y);
            g.pose().scale(0.5f, 0.5f);
            g.item(icon, 0, 0);
            g.pose().popMatrix();
        }
        // Capacity bar where vanilla draws item bars; never over an existing one (e.g. durability).
        if (!stack.isBarVisible()) {
            int bx = x + 2;
            int by = y + 13;
            g.fill(RenderPipelines.GUI, bx, by, bx + ShulkerSummary.BAR_WIDTH, by + 2, 0xFF000000);
            g.fill(RenderPipelines.GUI, bx, by, bx + summary.barWidth(), by + 1, summary.full() ? BAR_FULL : BAR);
        }
        decorated++;
    }

    /** Slots decorated so far; for development game tests. */
    public static long decorated() {
        return decorated;
    }
}
