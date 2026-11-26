package unified.qol.feature.foodhud;

import unified.qol.feature.foodhud.FoodMath.Prediction;
import unified.qol.ui.FeatureComparisons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The settings comparison for Food HUD (settings-ux-v1.md §8, Q-SU4): a fixed hunger bar of 12 food points and 3
 * saturation while holding bread, drawn as vanilla does and with the Food HUD's saturation overlay and food preview.
 * Uses the Food HUD's own math and sprites; independent of any player or world.
 */
public final class FoodHudComparison implements FeatureComparisons.Comparison {
    private static final int FOOD = 12;
    private static final float SATURATION = 3.0f;
    private static final int BREAD_NUTRITION = 5;
    private static final float BREAD_SATURATION = 6.0f;
    private static final Identifier EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final int ICONS = 10;
    private static final int STEP = 8;
    private static final int SIZE = 9;
    private static final int BAR = (ICONS - 1) * STEP + SIZE;

    // Built on first draw: item components are not bound yet while the client initializes.
    private ItemStack bread;

    public static void register() {
        FeatureComparisons.register(FoodHudSettings.ID, new FoodHudComparison());
    }

    @Override
    public int width() {
        return 20 + BAR;
    }

    @Override
    public int height() {
        return 16;
    }

    @Override
    public void draw(GuiGraphicsExtractor g, Font font, int x, int y, boolean qol) {
        // The held bread is only an illustration; item stacks exist only once a world is loaded.
        if (bread == null && FeatureComparisons.itemsAvailable()) {
            bread = new ItemStack(Items.BREAD);
        }
        if (bread != null) {
            g.item(bread, x, y);
        }
        int right = x + width();
        int barY = y + 4;
        Prediction prediction = FoodMath.afterEating(FOOD, SATURATION, BREAD_NUTRITION, BREAD_SATURATION);
        HungerPreview.Icon[] preview = HungerPreview.icons(true, true, FOOD, prediction.food());
        float flash = FoodHudRenderer.flashAlpha();
        for (int icon = 0; icon < ICONS; icon++) {
            // Vanilla fills the bar from the right, like the HUD.
            int ix = right - icon * STEP - SIZE;
            FoodHudRenderer.blit(g, EMPTY, ix, barY, 1.0f);
            if (icon * 2 + 1 < FOOD) {
                FoodHudRenderer.blit(g, FULL, ix, barY, 1.0f);
            } else if (icon * 2 + 1 == FOOD) {
                FoodHudRenderer.blit(g, HALF, ix, barY, 1.0f);
            }
            if (!qol) {
                continue;
            }
            int current = FoodMath.saturationQuarters(SATURATION, icon);
            if (current > 0) {
                FoodHudRenderer.blit(g, FoodHudRenderer.saturationSprite(current), ix, barY, 1.0f);
            }
            if (preview[icon] != HungerPreview.Icon.NONE) {
                FoodHudRenderer.blit(g, Identifier.withDefaultNamespace(HungerPreview.spritePath(preview[icon], false)),
                        ix, barY, flash);
            }
            int after = FoodMath.saturationQuarters(prediction.saturation(), icon);
            if (after > current) {
                FoodHudRenderer.blit(g, FoodHudRenderer.saturationSprite(after), ix, barY, flash);
            }
        }
    }
}
