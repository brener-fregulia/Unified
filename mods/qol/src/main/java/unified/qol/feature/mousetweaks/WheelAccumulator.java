package unified.qol.feature.mousetweaks;

/**
 * Whole wheel steps from fractional deltas (mouse-tweaks-v1.md §4.4). Each full 1.0 of accumulated delta is one
 * item; a fractional or high-resolution delta keeps its magnitude, and reversing direction discards the remainder,
 * like vanilla's ScrollWheelHandler. A step count is capped per event and the excess is discarded, never queued.
 */
final class WheelAccumulator {
    /** At most this many items per wheel event (Q-MT6). */
    static final int MAX_ITEMS_PER_EVENT = 8;

    private double accumulated;

    /** Adds one event's delta; returns the signed whole steps to perform now, at most 8 either way. */
    int add(double delta) {
        if (!Double.isFinite(delta) || delta == 0) {
            return 0;
        }
        if (accumulated != 0 && Math.signum(delta) != Math.signum(accumulated)) {
            accumulated = 0;
        }
        accumulated += delta;
        int steps = (int) accumulated; // truncates toward zero
        accumulated -= steps;
        return Math.clamp(steps, -MAX_ITEMS_PER_EVENT, MAX_ITEMS_PER_EVENT);
    }

    void reset() {
        accumulated = 0;
    }

    double remainder() {
        return accumulated;
    }
}
