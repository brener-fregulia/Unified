package io.github.brener_fregulia.qolunified.feature.foodhud;

/**
 * Tooltip icon layout (food-hud-v1.md §11.1a): 2 points per icon, a partial icon first (leftmost), then full icons.
 * No Minecraft types.
 */
public final class TooltipIcons {
    public static final int MAX_ICONS = 10;
    /** Sprite width in pixels. */
    public static final int SPRITE_WIDTH = 9;

    private TooltipIcons() {
    }

    /** Fill fraction (0 to 1] of each icon, left to right. Values above 10 icons are capped (all full). */
    public static float[] fills(float points) {
        if (!(points > 0.0f)) {
            return new float[0];
        }
        float icons = points / FoodMath.POINTS_PER_ICON;
        if (icons >= MAX_ICONS) {
            float[] all = new float[MAX_ICONS];
            java.util.Arrays.fill(all, 1.0f);
            return all;
        }
        int full = (int) Math.floor(icons + 1.0e-4f);
        float partial = icons - full;
        boolean hasPartial = partial > 1.0e-4f;
        float[] fills = new float[full + (hasPartial ? 1 : 0)];
        int i = 0;
        if (hasPartial) {
            fills[i++] = partial;
        }
        while (i < fills.length) {
            fills[i++] = 1.0f;
        }
        return fills;
    }

    /** Filled pixel columns from the left for a fill fraction; any value above zero shows at least one column. */
    public static int filledColumns(float fill) {
        if (!(fill > 0.0f)) {
            return 0;
        }
        return Math.clamp(Math.round(fill * SPRITE_WIDTH), 1, SPRITE_WIDTH);
    }
}
