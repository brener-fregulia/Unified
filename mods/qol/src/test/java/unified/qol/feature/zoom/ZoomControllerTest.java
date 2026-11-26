package unified.qol.feature.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.SettingsSnapshot;
import unified.qol.feature.zoom.ZoomController.State;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Zoom state machine, timing, scroll and FOV arithmetic (zoom-v1.md §5–§9, Z-AC-1 to Z-AC-12). */
class ZoomControllerTest {
    private static final long MS = 1_000_000L;
    private static final double FOV = 70;

    private long now = 1_000 * MS;
    private final ZoomController zoom = new ZoomController();

    /** The behavior tests use fixed timings and bounds, independent of the shipped defaults (M1.8.1). */
    private static final Map<String, Object> BASE = Map.of(ZoomSettings.MAX, 16, ZoomSettings.IN_MS, 250,
            ZoomSettings.OUT_MS, 150);

    {
        zoom.configure(config(Map.of()));
    }

    private static ZoomConfig config(Map<String, Object> overrides) {
        var values = new java.util.HashMap<String, Object>(BASE);
        values.putAll(overrides);
        return ZoomConfig.from(SettingsSnapshot.of(ZoomSettings.DESCRIPTOR, values));
    }

    /** Runs {@code totalMs} at {@code fps}, ending exactly at the total. */
    private void run(double totalMs, int fps) {
        long end = now + Math.round(totalMs * MS);
        long frame = 1_000_000_000L / fps;
        while (now < end) {
            now = Math.min(end, now + frame);
            zoom.advance(now);
        }
    }

    private void start() {
        zoom.advance(now); // the clock starts on the first frame
    }

    @Test
    void holdZoomsInToTheInitialMagnificationAndBackOut() {
        start();
        assertEquals(State.IDLE, zoom.state());
        zoom.hold(true);
        assertEquals(State.ZOOMING_IN, zoom.state());
        run(250, 60);
        assertEquals(State.ZOOMED, zoom.state());
        assertEquals(FOV / 4, zoom.fov(FOV), 1e-9, "Z-AC-1: 4× by default");
        zoom.hold(false);
        assertEquals(State.ZOOMING_OUT, zoom.state());
        run(150, 60);
        assertEquals(State.IDLE, zoom.state());
        assertEquals(FOV, zoom.fov(FOV), 1e-9, "Z-AC-8: vanilla FOV restored exactly");
        assertEquals(1, zoom.sensitivityScale(), 1e-12);
    }

    @Test
    void toggleStaysZoomedUntilPressedAgain() {
        zoom.configure(config(Map.of(ZoomSettings.MODE, ZoomSettings.MODE_TOGGLE)));
        start();
        zoom.togglePressed();
        zoom.hold(false); // key released: ignored in toggle mode
        run(300, 60);
        assertEquals(State.ZOOMED, zoom.state());
        zoom.togglePressed();
        run(200, 60);
        assertEquals(State.IDLE, zoom.state());
    }

    @Test
    void holdModeIgnoresTogglePresses() {
        start();
        zoom.togglePressed();
        assertEquals(State.IDLE, zoom.state());
    }

    @Test
    void transitionIsSmoothAndReversalNeverJumps() {
        start();
        zoom.hold(true);
        run(125, 240);
        double mid = zoom.fov(FOV);
        assertTrue(mid < FOV && mid > FOV / 4, "Z-AC-3: strictly between the endpoints");
        // Reverse half-way: the next frame stays within one small frame step of the previous FOV.
        zoom.hold(false);
        assertEquals(State.ZOOMING_OUT, zoom.state());
        double before = zoom.fov(FOV);
        run(1000.0 / 240, 240);
        assertTrue(Math.abs(zoom.fov(FOV) - before) < 2, "no jump on reversal");
        zoom.hold(true);
        run(1000.0 / 240, 240);
        assertEquals(State.ZOOMING_IN, zoom.state());
    }

    @Test
    void timingIsTheSameAtAnyFrameRate() {
        double[] at = new double[4];
        int[] rates = {30, 60, 144, 240};
        for (int i = 0; i < rates.length; i++) {
            ZoomController z = new ZoomController();
            long t = 0;
            z.advance(t);
            z.hold(true);
            long end = 100 * MS;
            long frame = 1_000_000_000L / rates[i];
            while (t < end) {
                t = Math.min(end, t + frame);
                z.advance(t);
            }
            at[i] = z.fov(FOV);
        }
        for (double v : at) {
            assertEquals(at[0], v, 1e-6, "Z-AC-4: same FOV after the same elapsed time");
        }
    }

    @Test
    void aFreezeCannotSkipTheAnimation() {
        start();
        zoom.hold(true);
        now += 5_000 * MS; // five-second hitch
        zoom.advance(now);
        assertTrue(zoom.progress() <= 100.0 / 250 + 1e-9, "one step is capped at 100 ms");
        zoom.advance(now - 50 * MS); // a clock going backwards is ignored
        assertTrue(zoom.progress() > 0);
    }

    @Test
    void curvesHaveTheirShapes() {
        assertEquals(0.875, ZoomConfig.Curve.SMOOTH.apply(0.5), 1e-12);
        assertEquals(0.5, ZoomConfig.Curve.LINEAR.apply(0.5), 1e-12);
        assertEquals(1, ZoomConfig.Curve.INSTANT.apply(0.01), 1e-12);
        for (ZoomConfig.Curve c : ZoomConfig.Curve.values()) {
            assertEquals(0, c.apply(0), 1e-12);
            assertEquals(1, c.apply(1), 1e-12);
        }
        zoom.configure(config(Map.of(ZoomSettings.TRANSITION, ZoomSettings.INSTANT)));
        start();
        zoom.hold(true);
        run(1, 60);
        assertEquals(State.ZOOMED, zoom.state(), "instant needs no animation time");
        zoom.configure(config(Map.of(ZoomSettings.IN_MS, 0)));
        zoom.hold(false);
        run(1, 60);
    }

    @Test
    void scrollKeepsTheMagnitudeOfFractionalAndFullNotches() {
        start();
        assertFalse(zoom.scroll(1), "Z-AC-6: not zoomed, the wheel stays vanilla");
        zoom.hold(true);
        run(250, 60);
        assertTrue(zoom.scroll(1));
        assertEquals(5, zoom.level(), 1e-9, "one notch up: 4 × 1.25");
        assertTrue(zoom.scroll(-1));
        assertEquals(4, zoom.level(), 1e-9, "one notch down");
        for (int i = 0; i < 4; i++) {
            zoom.scroll(0.25); // a high-resolution wheel reporting quarter notches
        }
        assertEquals(5, zoom.level(), 1e-9, "four quarter notches equal one notch");
        zoom.scroll(0.5);
        zoom.scroll(-0.5); // rapid reversal returns exactly
        assertEquals(5, zoom.level(), 1e-9);
        zoom.scroll(2);
        assertEquals(5 * 1.25 * 1.25, zoom.level(), 1e-9, "multiple notches in one event");
        zoom.scroll(Double.NaN);
        assertEquals(5 * 1.25 * 1.25, zoom.level(), 1e-9, "a malformed delta is ignored");
    }

    @Test
    void scrollStaysWithinTheBoundsAndShowsTheLevelSmoothly() {
        start();
        zoom.hold(true);
        run(250, 60);
        for (int i = 0; i < 50; i++) {
            zoom.scroll(1);
        }
        assertEquals(16, zoom.level(), 1e-9, "Z-AC-5: upper bound");
        for (int i = 0; i < 50; i++) {
            zoom.scroll(-1);
        }
        assertEquals(2, zoom.level(), 1e-9, "lower bound");
        zoom.scroll(4);
        double target = zoom.level();
        run(16, 60);
        assertTrue(zoom.shownLevel() < target, "the shown level follows instead of jumping");
        run(1000, 60);
        assertEquals(target, zoom.shownLevel(), 1e-3);
    }

    @Test
    void scrollIsVanillaAgainAsSoonAsZoomOutStarts() {
        start();
        zoom.hold(true);
        run(250, 60);
        zoom.hold(false);
        assertEquals(State.ZOOMING_OUT, zoom.state());
        assertFalse(zoom.scroll(1));
        zoom.configure(config(Map.of(ZoomSettings.SCROLL, false)));
        zoom.hold(true);
        assertFalse(zoom.scroll(1), "scroll zoom off");
    }

    @Test
    void scrolledLevelResetsBetweenActivations() {
        start();
        zoom.hold(true);
        run(250, 60);
        zoom.scroll(3);
        zoom.hold(false);
        run(150, 60);
        zoom.hold(true);
        assertEquals(4, zoom.level(), 1e-9, "Q-Z6");
    }

    @Test
    void sensitivityFollowsTheAppliedMagnification() {
        start();
        zoom.hold(true);
        run(250, 60);
        zoom.fov(FOV);
        assertEquals(0.25, zoom.sensitivityScale(), 1e-9, "Z-AC-7: a quarter at 4×");
        zoom.hold(false);
        run(75, 60);
        zoom.fov(FOV);
        assertTrue(zoom.sensitivityScale() > 0.25 && zoom.sensitivityScale() < 1, "smooth during the transition");
        zoom.configure(config(Map.of(ZoomSettings.REDUCE_SENSITIVITY, false)));
        assertEquals(1, zoom.sensitivityScale(), 1e-12);
    }

    @Test
    void theFovFloorHoldsWithTheSpyglass() {
        zoom.configure(config(Map.of(ZoomSettings.MAX, 50, ZoomSettings.INITIAL, 10)));
        start();
        zoom.hold(true);
        run(250, 60);
        double spyglass = FOV * 0.1; // vanilla's scoping FOV modifier
        assertEquals(1.0, zoom.fov(spyglass), 1e-9, "Z-AC-12: floor of 1°");
        assertEquals(spyglass, 1 / zoom.sensitivityScale(), 1e-9, "sensitivity follows the clamped factor");
        assertEquals(0.5, zoom.fov(0.5), 1e-9, "a FOV already below the floor is never raised");
    }

    @Test
    void releaseAndResetEndTheZoom() {
        zoom.configure(config(Map.of(ZoomSettings.MODE, ZoomSettings.MODE_TOGGLE)));
        start();
        zoom.togglePressed();
        run(250, 60);
        zoom.release(); // Z-AC-9: a menu opened
        assertEquals(State.ZOOMING_OUT, zoom.state());
        run(150, 60);
        assertEquals(State.IDLE, zoom.state(), "toggle does not come back after the menu");
        zoom.togglePressed();
        run(100, 60);
        zoom.reset(); // Z-AC-10: disabled or disconnected
        assertEquals(State.IDLE, zoom.state());
        assertEquals(FOV, zoom.fov(FOV), 1e-12);
    }

    @Test
    void liveSettingsChangesTakeEffectWhileZoomed() {
        start();
        zoom.hold(true);
        run(250, 60);
        zoom.scroll(4); // about 9.8×
        zoom.configure(config(Map.of(ZoomSettings.MAX, 6)));
        assertEquals(6, zoom.level(), 1e-9, "a lower maximum re-clamps at once");
        zoom.configure(config(Map.of(ZoomSettings.OUT_MS, 1000)));
        zoom.hold(false);
        run(500, 60);
        assertEquals(State.ZOOMING_OUT, zoom.state(), "the new zoom-out time is used");
    }
}
