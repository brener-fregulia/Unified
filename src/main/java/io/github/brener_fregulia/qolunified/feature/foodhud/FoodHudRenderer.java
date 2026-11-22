package io.github.brener_fregulia.qolunified.feature.foodhud;

import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.feature.foodhud.FoodMath.Prediction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;

/**
 * Draws the Food HUD overlays on top of the vanilla hunger bar and hearts. The Mixins record where vanilla drew each
 * icon during the current call and hand them over at its end; positions never outlive that call.
 */
public final class FoodHudRenderer {
    private static final int ICONS = 10;
    private static final int ICON_SIZE = 9;
    private static final int ICON_SPACING = 8;

    private static final Identifier[] SATURATION = {
            null,
            sprite("food_hud/saturation_1"), sprite("food_hud/saturation_2"),
            sprite("food_hud/saturation_3"), sprite("food_hud/saturation_4")};
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier FOOD_FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");
    private static final Identifier FOOD_HALF_HUNGER = Identifier.withDefaultNamespace("hud/food_half_hunger");

    // Food icon positions recorded during the current Hud.extractFood call (render thread only).
    private static final int[] foodY = new int[ICONS];
    private static int foodIcons = -1;

    private FoodHudRenderer() {
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath("qol_unified", path);
    }

    // ---- Hunger bar (Mixin M-1) ----

    public static void beginFood() {
        foodIcons = FoodHudFeature.activeSettings() == null ? -1 : 0;
    }

    public static void recordFoodIcon(int y) {
        if (foodIcons >= 0 && foodIcons < ICONS) {
            foodY[foodIcons++] = y;
        }
    }

    public static void endFood(GuiGraphicsExtractor graphics, Player player, int baseY, int right) {
        int recorded = foodIcons;
        foodIcons = -1;
        SettingsSnapshot settings = FoodHudFeature.activeSettings();
        if (settings == null || recorded != ICONS || player == null) {
            return;
        }
        boolean animate = settings.getBoolean(FoodHudSettings.MATCH_ANIMATIONS);
        boolean saturationOverlay = settings.getBoolean(FoodHudSettings.SATURATION_OVERLAY);
        FoodData data = player.getFoodData();
        int food = data.getFoodLevel();
        float saturation = data.getSaturationLevel();

        Prediction prediction = null;
        if (settings.getBoolean(FoodHudSettings.FOOD_PREVIEW)) {
            FoodProperties held = HeldFood.previewed(player, settings.getBoolean(FoodHudSettings.OFFHAND_PREVIEW));
            if (held != null) {
                prediction = FoodMath.afterEating(food, saturation, held.nutrition(), held.saturation());
            }
        }
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        float flash = flashAlpha();

        for (int icon = 0; icon < ICONS; icon++) {
            int x = right - icon * ICON_SPACING - ICON_SIZE;
            int y = animate ? foodY[icon] : baseY;
            int current = FoodMath.saturationQuarters(saturation, icon);
            if (saturationOverlay && current > 0) {
                blit(graphics, SATURATION[current], x, y, 1.0f);
            }
            if (prediction == null) {
                continue;
            }
            int foodNow = FoodMath.iconHalves(food, icon);
            int foodAfter = FoodMath.iconHalves(prediction.food(), icon);
            if (foodAfter > foodNow) {
                Identifier id = foodAfter == 2 ? (hunger ? FOOD_FULL_HUNGER : FOOD_FULL)
                        : (hunger ? FOOD_HALF_HUNGER : FOOD_HALF);
                blit(graphics, id, x, y, flash);
            }
            int saturationAfter = FoodMath.saturationQuarters(prediction.saturation(), icon);
            if (saturationOverlay && saturationAfter > current) {
                blit(graphics, SATURATION[saturationAfter], x, y, flash);
            }
        }
    }

    static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, float alpha) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, ICON_SIZE, ICON_SIZE, alpha);
    }

    /** Blinking opacity for previews, between 0.1 and 0.65, so they read as "would be", not "is". */
    static float flashAlpha() {
        float phase = (Util.getMillis() % 1500L) / 1500.0f;
        return 0.1f + 0.55f * (0.5f - 0.5f * Mth.cos(phase * Mth.TWO_PI));
    }
}
