package unified.qol.feature.foodhud;

/**
 * Which vanilla hunger sprite the HUD preview draws in each of the 10 hunger-bar slots (food-hud-v1.md §11.1b).
 * Decided from the absolute predicted hunger, not from the restoration alone. No Minecraft types.
 */
public final class HungerPreview {
    public static final int SLOTS = 10;

    /** Sprite for one slot: nothing, vanilla's half shank or vanilla's full shank. */
    public enum Icon {
        NONE,
        HALF,
        FULL
    }

    private HungerPreview() {
    }

    /**
     * Preview icons per slot, slot 0 being vanilla's first (rightmost) icon. A slot gets an icon only where the
     * predicted level adds to it: a half when it would hold one point, a full shank when it would hold two (which
     * also completes an existing half). The prediction is clamped to 20. All {@code NONE} when the feature or the
     * preview is off, or there is no held food.
     */
    public static Icon[] icons(boolean active, boolean previewEnabled, int food, Integer predictedFood) {
        Icon[] icons = new Icon[SLOTS];
        java.util.Arrays.fill(icons, Icon.NONE);
        if (!active || !previewEnabled || predictedFood == null) {
            return icons;
        }
        int now = Math.clamp(food, 0, FoodMath.MAX_FOOD);
        int after = Math.clamp(predictedFood, now, FoodMath.MAX_FOOD);
        for (int slot = 0; slot < SLOTS; slot++) {
            int before = FoodMath.iconHalves(now, slot);
            int later = FoodMath.iconHalves(after, slot);
            if (later > before) {
                icons[slot] = later == 2 ? Icon.FULL : Icon.HALF;
            }
        }
        return icons;
    }

    /** Vanilla sprite path for an icon, using the Hunger-effect variants when that effect is active. */
    public static String spritePath(Icon icon, boolean hungerEffect) {
        String suffix = hungerEffect ? "_hunger" : "";
        return switch (icon) {
            case FULL -> "hud/food_full" + suffix;
            case HALF -> "hud/food_half" + suffix;
            case NONE -> null;
        };
    }
}
