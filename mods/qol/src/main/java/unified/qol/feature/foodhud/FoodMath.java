package unified.qol.feature.foodhud;

/**
 * Pure food and regeneration rules from Minecraft 26.3 (food-hud-v1.md §3). No Minecraft types, so they are unit
 * tested directly. Nothing here changes game state.
 */
public final class FoodMath {
    public static final int MAX_FOOD = 20;
    /** Points shown per HUD icon (a full shank or heart). */
    public static final int POINTS_PER_ICON = 2;

    // FoodData.tick constants (26.3).
    static final float EXHAUSTION_PER_DROP = 4.0f;
    static final float FAST_HEAL_MAX_SATURATION = 6.0f;
    static final float SLOW_HEAL_EXHAUSTION = 6.0f;
    static final int FAST_HEAL_FOOD = 20;
    static final int SLOW_HEAL_FOOD = 18;
    private static final int MAX_STEPS = 10_000;

    private FoodMath() {
    }

    /** Food and saturation after eating, as {@code FoodData.add} computes them. */
    public record Prediction(int food, float saturation) {
    }

    public static Prediction afterEating(int food, float saturation, int nutrition, float foodSaturation) {
        int newFood = clamp(food + nutrition, 0, MAX_FOOD);
        float newSaturation = Math.clamp(saturation + foodSaturation, 0.0f, (float) newFood);
        return new Prediction(newFood, newSaturation);
    }

    /** {@code Player.canEat}: invulnerable players (Creative), food that can always be eaten, or not full. */
    public static boolean canEat(boolean invulnerable, boolean canAlwaysEat, int food) {
        return invulnerable || canAlwaysEat || food < MAX_FOOD;
    }

    /**
     * Estimated health that natural regeneration restores from the given food state, capped at missing health.
     *
     * <p>Simulates {@code FoodData.tick} with exhaustion starting at 0 and no other exhaustion (the client does not
     * know the real value). Peaceful regenerates regardless of food, so no gain is attributed to food there. Returns
     * 0 when regeneration is known to be off.
     */
    public static float estimateHealthGain(int food, float saturation, float health, float maxHealth,
            boolean peaceful, boolean naturalRegeneration) {
        if (peaceful || !naturalRegeneration || !(health < maxHealth) || !Float.isFinite(health)
                || !Float.isFinite(maxHealth) || !Float.isFinite(saturation)) {
            return 0.0f;
        }
        float hp = health;
        float sat = Math.max(0.0f, saturation);
        int f = clamp(food, 0, MAX_FOOD);
        float exhaustion = 0.0f;
        for (int step = 0; step < MAX_STEPS && hp < maxHealth; step++) {
            while (exhaustion > EXHAUSTION_PER_DROP) {
                exhaustion -= EXHAUSTION_PER_DROP;
                if (sat > 0.0f) {
                    sat = Math.max(sat - 1.0f, 0.0f);
                } else {
                    f = Math.max(f - 1, 0);
                }
            }
            if (sat > 0.0f && f >= FAST_HEAL_FOOD) {
                float used = Math.min(sat, FAST_HEAL_MAX_SATURATION);
                hp += used / FAST_HEAL_MAX_SATURATION;
                exhaustion += used;
            } else if (f >= SLOW_HEAL_FOOD) {
                hp += 1.0f;
                exhaustion += SLOW_HEAL_EXHAUSTION;
            } else {
                break;
            }
        }
        return Math.min(hp, maxHealth) - health;
    }

    /** Points (0 to 2) of {@code value} that fall on HUD icon {@code icon}, counted from the first icon. */
    public static float iconPoints(float value, int icon) {
        return Math.clamp(value - (float) icon * POINTS_PER_ICON, 0.0f, (float) POINTS_PER_ICON);
    }

    /** Saturation shown on an icon in quarter steps (0 to 4); any saturation on the icon shows at least one. */
    public static int saturationQuarters(float saturation, int icon) {
        return (int) Math.ceil(iconPoints(saturation, icon) * 2.0f - 1.0e-4f);
    }

    /** Whole/half points on an icon (0, 1 or 2) for food or health, rounding the way the vanilla HUD does. */
    public static int iconHalves(float value, int icon) {
        return (int) Math.ceil(iconPoints(value, icon) - 1.0e-4f);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
