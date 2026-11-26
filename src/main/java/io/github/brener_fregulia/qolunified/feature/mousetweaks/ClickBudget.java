package io.github.brener_fregulia.qolunified.feature.mousetweaks;

/**
 * Generated vanilla clicks per client tick and screen (Q-MT7). A multi-click sequence reserves all of its clicks
 * before the first one is sent, so it is never started without the budget to finish it. Nothing is queued.
 */
final class ClickBudget {
    static final int CLICKS_PER_TICK = 24;

    private long tick = Long.MIN_VALUE;
    private int used;

    /** Reserves {@code clicks} in {@code currentTick}; false (and nothing reserved) if they do not all fit. */
    boolean reserve(long currentTick, int clicks) {
        if (currentTick != tick) {
            tick = currentTick;
            used = 0;
        }
        if (clicks <= 0 || used + clicks > CLICKS_PER_TICK) {
            return false;
        }
        used += clicks;
        return true;
    }

    int used(long currentTick) {
        return currentTick == tick ? used : 0;
    }
}
