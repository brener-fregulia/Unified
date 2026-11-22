package io.github.brener_fregulia.qolunified.feature.foodhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;

/**
 * Two rows: hunger restored, then saturation restored, each as icons (2 points per icon, partial icons filling from
 * left to right) followed by the exact value, for example {@code +5} and {@code +6.0}. The values come from the
 * stack's own food component, so custom foods show their real numbers.
 */
final class FoodTooltipComponent implements ClientTooltipComponent {
    private static final int MAX_ICONS = 10;
    private static final int SPACING = 8;
    private static final int SIZE = 9;
    private static final int ROW = 10;
    private static final int TEXT_GAP = 3;
    private static final int HUNGER_TEXT = 0xFFE0E0E0;
    private static final int SATURATION_TEXT = 0xFFFFD45A;
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");

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
        return Math.min(MAX_ICONS, Math.max(0, (int) Math.ceil(points / FoodMath.POINTS_PER_ICON - 1.0e-4f)));
    }

    private boolean saturationRow() {
        return food.saturation() > 0.0f;
    }

    /** Numbers start in one column after the wider icon row, so both rows line up. */
    private int textX() {
        int icons = Math.max(icons(food.nutrition()), saturationRow() ? icons(food.saturation()) : 0);
        return icons == 0 ? 0 : (icons - 1) * SPACING + SIZE + TEXT_GAP;
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
        for (int i = 0; i < icons(food.nutrition()); i++) {
            FoodHudRenderer.blitFood(graphics, FOOD_FULL, FoodMath.iconHalves(food.nutrition(), i),
                    x + i * SPACING, y, 1.0f);
        }
        graphics.text(font, hungerText, textX, y + 1, HUNGER_TEXT, true);
        if (!saturationRow()) {
            return;
        }
        int sy = y + ROW;
        for (int i = 0; i < icons(food.saturation()); i++) {
            FoodHudRenderer.blit(graphics, FoodHudRenderer.saturationSprite(
                    FoodMath.saturationQuarters(food.saturation(), i)), x + i * SPACING, sy, 1.0f);
        }
        graphics.text(font, saturationText, textX, sy + 1, SATURATION_TEXT, true);
    }
}
