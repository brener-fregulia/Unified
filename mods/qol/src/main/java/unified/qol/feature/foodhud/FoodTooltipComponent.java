package unified.qol.feature.foodhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;

/**
 * Two rows: hunger restored, then saturation restored, each as icons (2 points per icon; a partial icon comes first
 * and shows its silhouette with the exact fraction filled from the right) followed by the exact value, for example {@code +5} and {@code +6.0}. The values come from the
 * stack's own food component, so custom foods show their real numbers.
 */
final class FoodTooltipComponent implements ClientTooltipComponent {
    private static final int SPACING = 8;
    private static final int SIZE = 9;
    private static final int ROW = 10;
    private static final int TEXT_GAP = 3;
    private static final int HUNGER_TEXT = 0xFFE0E0E0;
    private static final int SATURATION_TEXT = 0xFFFFD45A;
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    // Tooltip drumsticks use the vanilla shank's 7x7 footprint so both rows have the same height.
    private static final Identifier SATURATION_FULL = Identifier.fromNamespaceAndPath("qol_unified",
            "food_hud/saturation_tooltip");
    private static final Identifier SATURATION_EMPTY = Identifier.fromNamespaceAndPath("qol_unified",
            "food_hud/saturation_tooltip_empty");
    private static final Identifier[] SATURATION_PARTIAL = {null,
            Identifier.fromNamespaceAndPath("qol_unified", "food_hud/saturation_tooltip_1"),
            Identifier.fromNamespaceAndPath("qol_unified", "food_hud/saturation_tooltip_2"),
            Identifier.fromNamespaceAndPath("qol_unified", "food_hud/saturation_tooltip_3")};

    private final FoodTooltip food;
    private final String hungerText;
    private final String saturationText;

    FoodTooltipComponent(FoodTooltip food) {
        this.food = food;
        this.hungerText = FoodTooltipText.hunger(food.nutrition());
        this.saturationText = FoodTooltipText.saturation(food.saturation(),
                Minecraft.getInstance().getLanguageManager().getSelected());
    }

    private static int icons(float points) {
        return TooltipIcons.fills(points).length;
    }

    private boolean saturationRow() {
        return food.saturation() > 0.0f;
    }

    /** Numbers start in one column after the wider icon row, so both rows line up. */
    private int textX() {
        int icons = Math.max(icons(food.nutrition()), saturationRow() ? icons(food.saturation()) : 0);
        return icons == 0 ? 0 : (icons - 1) * SPACING + SIZE + TEXT_GAP;
    }

    /**
     * Hunger row, left to right: a half shank (odd hunger) comes first and is vanilla's empty shank with vanilla's own
     * half-shank sprite on top (filled on its right side), followed by full shanks.
     */
    private static void hungerRow(GuiGraphicsExtractor graphics, float[] fills, int x, int y) {
        for (int i = 0; i < fills.length; i++) {
            int px = x + i * SPACING;
            if (fills[i] >= 1.0f) {
                FoodHudRenderer.blit(graphics, FOOD_FULL, px, y, 1.0f);
            } else {
                FoodHudRenderer.blit(graphics, FOOD_EMPTY, px, y, 1.0f);
                FoodHudRenderer.blit(graphics, FOOD_HALF, px, y, 1.0f);
            }
        }
    }

    /**
     * Saturation row: the partial icon (rounded up to a quarter) is the dim silhouette with a hand-drawn quarter, half
     * or three-quarter golden fill that grows from the right edge of the meat.
     */
    private static void saturationRow(GuiGraphicsExtractor graphics, float[] fills, int x, int y) {
        for (int i = 0; i < fills.length; i++) {
            int px = x + i * SPACING;
            int quarters = TooltipIcons.quarters(fills[i]);
            if (quarters == 4) {
                FoodHudRenderer.blit(graphics, SATURATION_FULL, px, y, 1.0f);
            } else {
                FoodHudRenderer.blit(graphics, SATURATION_EMPTY, px, y, 1.0f);
                FoodHudRenderer.blit(graphics, SATURATION_PARTIAL[quarters], px, y, 1.0f);
            }
        }
    }

    @Override
    public int getHeight(Font font) {
        return (saturationRow() ? 2 : 1) * ROW + 2;
    }

    @Override
    public int getWidth(Font font) {
        int text = font.width(hungerText);
        if (saturationRow()) {
            text = Math.max(text, font.width(saturationText));
        }
        return textX() + text;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int textX = x + textX();
        hungerRow(graphics, TooltipIcons.fills(food.nutrition()), x, y);
        graphics.text(font, hungerText, textX, y + 1, HUNGER_TEXT, true);
        if (!saturationRow()) {
            return;
        }
        int sy = y + ROW;
        saturationRow(graphics, TooltipIcons.saturationFills(food.saturation()), x, sy);
        graphics.text(font, saturationText, textX, sy + 1, SATURATION_TEXT, true);
    }
}
