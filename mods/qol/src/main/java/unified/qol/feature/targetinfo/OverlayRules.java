package unified.qol.feature.targetinfo;

/** When and where the overlay is drawn (target-info-v1.md §5.3, §8). Pure. */
public final class OverlayRules {
    /** Gap above the overlay without boss bars. */
    public static final int TOP = 4;
    // Vanilla BossHealthOverlay (26.3): the first bar at y 12, one bar every 19 px, none at or below a third of the
    // screen; a bar is 5 px tall.
    static final int BOSS_FIRST_Y = 12;
    static final int BOSS_STEP = 19;
    static final int BOSS_BAR_HEIGHT = 5;

    public enum Target {
        NONE,
        BLOCK,
        ENTITY
    }

    private OverlayRules() {
    }

    public static boolean visible(boolean active, Target target, boolean showBlocks, boolean showEntities,
            boolean hudHidden, boolean screenOpen, boolean debugScreen) {
        if (!active || hudHidden || screenOpen || debugScreen) {
            return false;
        }
        return switch (target) {
            case NONE -> false;
            case BLOCK -> showBlocks;
            case ENTITY -> showEntities;
        };
    }

    /** Top of the overlay: below the last boss bar vanilla draws. */
    public static int top(int bossBars, int guiHeight) {
        int drawn = 0;
        for (int y = BOSS_FIRST_Y; drawn < bossBars && y < guiHeight / 3; y += BOSS_STEP) {
            drawn++;
        }
        if (drawn == 0) {
            return TOP;
        }
        return BOSS_FIRST_Y + (drawn - 1) * BOSS_STEP + BOSS_BAR_HEIGHT + TOP;
    }

    /**
     * Mining progress, 0–1, or a negative value when no bar is drawn: only while the client is breaking exactly the
     * targeted block, outside Creative, and not for instant breaks.
     */
    public static float progress(boolean destroying, boolean sameBlock, boolean creative, boolean instant,
            float progress) {
        if (!destroying || !sameBlock || creative || instant) {
            return -1.0f;
        }
        return Math.clamp(progress, 0.0f, 1.0f);
    }
}
