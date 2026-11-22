package io.github.brener_fregulia.qolunified.feature.foodhud;

/**
 * Which held food would be eaten on "use", following Minecraft 26.3's item-use order (food-hud-v1.md §11).
 *
 * <p>{@code Minecraft.startUseItem} tries the main hand, then the off-hand, and stops at the first hand whose use
 * succeeds. Food that cannot be eaten now fails ({@code Consumable.startConsuming}), so the off-hand is tried next.
 * Other consumables (potions, milk) and items with their own use action stop at the main hand.
 */
public final class HandSelection {
    /** What using a held stack would do, as far as the client can tell without running the use. */
    public enum Use {
        EMPTY,
        /** No use action is known: the use passes on to the off-hand. */
        NO_USE,
        EDIBLE_FOOD,
        /** Food that cannot be eaten now: the use fails and passes on to the off-hand. */
        INEDIBLE_FOOD,
        /** A consumable that is not food, or an item with its own use action: the off-hand is not reached. */
        OTHER_USE
    }

    public enum Hand {
        MAIN,
        OFF,
        NONE
    }

    private HandSelection() {
    }

    public static Hand select(Use main, Use off, boolean offhandPreview) {
        if (main == Use.EDIBLE_FOOD) {
            return Hand.MAIN;
        }
        boolean mainPasses = main == Use.EMPTY || main == Use.NO_USE || main == Use.INEDIBLE_FOOD;
        return mainPasses && offhandPreview && off == Use.EDIBLE_FOOD ? Hand.OFF : Hand.NONE;
    }
}
