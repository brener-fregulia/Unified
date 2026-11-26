package io.github.brener_fregulia.qolunified.feature.mousetweaks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Wheel accumulation, click pacing and wheel direction (mouse-tweaks-v1.md §4.4, §6.3). */
class MouseTweaksLogicTest {
    @Test
    void fullNotchesAreOneItemEach() {
        var wheel = new WheelAccumulator();
        assertEquals(1, wheel.add(1));
        assertEquals(-1, wheel.add(-1));
        assertEquals(3, wheel.add(3), "several notches in one event");
    }

    @Test
    void fractionalDeltasKeepTheirMagnitude() {
        var wheel = new WheelAccumulator();
        assertEquals(0, wheel.add(0.25));
        assertEquals(0, wheel.add(0.25));
        assertEquals(0, wheel.add(0.25));
        assertEquals(1, wheel.add(0.25), "four quarter notches are one item");
        assertEquals(0, wheel.remainder(), 1e-12);
        assertEquals(1, wheel.add(1.5));
        assertEquals(0.5, wheel.remainder(), 1e-12, "the half is kept for the next event");
    }

    @Test
    void reversingDiscardsTheRemainder() {
        var wheel = new WheelAccumulator();
        wheel.add(0.75);
        assertEquals(0, wheel.add(-0.5), "the reversal starts from zero");
        assertEquals(-0.5, wheel.remainder(), 1e-12);
        assertEquals(0, wheel.add(Double.NaN));
        assertEquals(0, wheel.add(0));
    }

    @Test
    void oneEventMovesAtMostEightItemsAndDiscardsTheRest() {
        var wheel = new WheelAccumulator();
        assertEquals(8, wheel.add(20));
        assertEquals(0, wheel.remainder(), 1e-12, "excess input is not queued");
        assertEquals(-8, wheel.add(-50.5));
    }

    @Test
    void theClickBudgetIsPerTickAndNeverSplitsASequence() {
        var budget = new ClickBudget();
        for (int i = 0; i < 8; i++) {
            assertTrue(budget.reserve(1, 3), "eight three-click transfers fit in 24");
        }
        assertFalse(budget.reserve(1, 1), "budget exhausted");
        assertEquals(24, budget.used(1));
        var partial = new ClickBudget();
        assertTrue(partial.reserve(5, 22));
        assertFalse(partial.reserve(5, 3), "a sequence that cannot finish is not started");
        assertEquals(22, partial.used(5), "and reserves nothing");
        assertTrue(partial.reserve(5, 2));
        assertTrue(partial.reserve(6, 3), "a new tick has a new budget");
        assertFalse(partial.reserve(6, 0));
    }

    @Test
    void wheelDirectionFollowsTheSetting() {
        var normal = MouseTweaksConfig.from(SettingsSnapshot.defaults(MouseTweaksSettings.DESCRIPTOR));
        assertTrue(normal.pushAmount(-1) > 0, "scrolling down pushes");
        assertTrue(normal.pushAmount(1) < 0, "scrolling up pulls");
        assertTrue(normal.lastToFirst());
        var inverted = MouseTweaksConfig.from(SettingsSnapshot.of(MouseTweaksSettings.DESCRIPTOR,
                Map.of("wheel_direction", "inverted", "wheel_search_order", "first_to_last")));
        assertTrue(inverted.pushAmount(1) > 0);
        assertFalse(inverted.lastToFirst());
    }
}
