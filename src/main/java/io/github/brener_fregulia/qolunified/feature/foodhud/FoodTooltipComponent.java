package io.github.brener_fregulia.qolunified.feature.foodhud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;

/** Two icon rows: hunger restored, then saturation restored. */
final class FoodTooltipComponent implements ClientTooltipComponent {
    private static final int MAX_ICONS = 10;
    private static final int SPACING = 8;
    private static final int SIZE = 9;
    private static final int ROW = 10;
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");

    private final FoodTooltip food;

    FoodTooltipComponent(FoodTooltip food) {
        this.food = food;
    }

    private static int icons(float points) {
        return Math.min(MAX_ICONS, Math.max(0, (int) Math.ceil(points / FoodMath.POINTS_PER_ICON - 1.0e-4f)));
    }

    private static boolean overflow(float points) {
        return points > MAX_ICONS * FoodMath.POINTS_PER_ICON;
    }

    private int rows() {
        return food.saturation() > 0.0f ? 2 : 1;
    }

    @Override
    public int getHeight(Font font) {
        return rows() * ROW + 2;
    }

    @Override
    public int getWidth(Font font) {
        int icons = Math.max(1, Math.max(icons(food.nutrition()), icons(food.saturation())));
        int width = (icons - 1) * SPACING + SIZE;
        return overflow(food.nutrition()) || overflow(food.saturation()) ? width + 2 + font.width("+") : width;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int nutritionIcons = icons(food.nutrition());
        for (int i = 0; i < nutritionIcons; i++) {
            int px = x + i * SPACING;
            int halves = FoodMath.iconHalves(food.nutrition(), i);
            FoodHudRenderer.blit(graphics, halves == 2 ? FOOD_FULL : FOOD_HALF, px, y, 1.0f);
        }
        plus(font, graphics, x + nutritionIcons * SPACING + 1, y, overflow(food.nutrition()));
        if (rows() < 2) {
            return;
        }
        int sy = y + ROW;
        int saturationIcons = icons(food.saturation());
        for (int i = 0; i < saturationIcons; i++) {
            int px = x + i * SPACING;
            FoodHudRenderer.blit(graphics, FoodHudRenderer.saturationSprite(
                    FoodMath.saturationQuarters(food.saturation(), i)), px, sy, 1.0f);
        }
        plus(font, graphics, x + saturationIcons * SPACING + 1, sy, overflow(food.saturation()));
    }

    private static void plus(Font font, GuiGraphicsExtractor graphics, int x, int y, boolean show) {
        if (show) {
            graphics.text(font, "+", x, y + 1, 0xFFFFFFFF, true);
        }
    }
}
