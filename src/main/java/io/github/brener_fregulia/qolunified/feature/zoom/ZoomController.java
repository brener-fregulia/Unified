package io.github.brener_fregulia.qolunified.feature.zoom;

/**
 * The zoom state machine and its arithmetic (zoom-v1.md §5–§9). Pure Java with primitive fields: no Minecraft types,
 * no allocation per frame, so it is unit-tested directly.
 *
 * <p>A single {@code progress} p in [0, 1] drives both directions: zooming in raises it at 1/zoom_in_ms, zooming out
 * lowers it at 1/zoom_out_ms. Reversing mid-way continues from the same p, so the FOV never jumps. Time comes from
 * the caller in nanoseconds and each step is capped, so a freeze cannot skip an animation.
 */
final class ZoomController {
    enum State { IDLE, ZOOMING_IN, ZOOMED, ZOOMING_OUT }

    /** Effective FOV never goes below this, in degrees (Q-Z5). */
    static final double MIN_FOV = 1.0;
    /** Magnification factor of one full wheel notch (Q-Z4). */
    static final double STEP = 1.25;
    /** Longest time step accepted from one frame to the next. */
    static final long MAX_STEP_NANOS = 100_000_000L;
    /** Time constant with which the shown level follows a scrolled level. */
    static final double FOLLOW_SECONDS = 0.08;
    private static final double EPSILON = 1e-9;

    private ZoomConfig config = ZoomConfig.DEFAULTS;
    private boolean requested;
    private double progress;
    private double level = config.initial();
    private double shownLevel = level;
    private long lastNanos = Long.MIN_VALUE;
    /** vanillaFov / zoomedFov of the last frame, 1 when not zoomed; drives the sensitivity scale. */
    private double appliedFactor = 1;

    void configure(ZoomConfig config) {
        this.config = config;
        level = clamp(level);
        if (!requested && progress == 0) {
            level = shownLevel = config.initial();
        }
    }

    ZoomConfig config() {
        return config;
    }

    /** Hold mode: the key's current state, sampled every tick and frame. */
    void hold(boolean down) {
        if (!config.toggle()) {
            request(down);
        }
    }

    /** Toggle mode: one press of the key. */
    void togglePressed() {
        if (config.toggle()) {
            request(!requested);
        }
    }

    /** A reset condition (menu, pause, focus loss, death, respawn, dimension change): zoom out smoothly. */
    void release() {
        request(false);
    }

    /** Feature disabled or world left: no zoom at once. */
    void reset() {
        requested = false;
        progress = 0;
        level = shownLevel = config.initial();
        appliedFactor = 1;
        lastNanos = Long.MIN_VALUE;
    }

    private void request(boolean on) {
        if (on && !requested && progress == 0) {
            level = shownLevel = config.initial(); // Q-Z6: each activation starts at the initial magnification
        }
        requested = on;
    }

    State state() {
        if (requested) {
            return progress >= 1 ? State.ZOOMED : State.ZOOMING_IN;
        }
        return progress > 0 ? State.ZOOMING_OUT : State.IDLE;
    }

    boolean idle() {
        return !requested && progress == 0;
    }

    /** Advances the animation to {@code nanos}; the first call after idling only starts the clock. */
    void advance(long nanos) {
        if (lastNanos == Long.MIN_VALUE || idle()) {
            lastNanos = nanos;
            return;
        }
        long step = Math.clamp(nanos - lastNanos, 0, MAX_STEP_NANOS);
        lastNanos = nanos;
        double ms = step / 1_000_000.0;
        if (requested) {
            progress = config.inMs() == 0 || config.curve() == ZoomConfig.Curve.INSTANT
                    ? 1 : Math.min(1, progress + ms / config.inMs());
        } else {
            progress = config.outMs() == 0 || config.curve() == ZoomConfig.Curve.INSTANT
                    ? 0 : Math.max(0, progress - ms / config.outMs());
        }
        if (progress > 1 - EPSILON) {
            progress = 1; // summed frame steps must still land exactly on the endpoints
        } else if (progress < EPSILON) {
            progress = 0;
        }
        double follow = 1 - Math.exp(-(ms / 1000.0) / FOLLOW_SECONDS);
        shownLevel += (level - shownLevel) * follow;
        if (progress == 0 && !requested) {
            level = shownLevel = config.initial();
        }
    }

    /**
     * Scroll input while zoom owns the wheel. {@code notches} keeps its magnitude: one full notch multiplies by
     * {@link #STEP}, a fractional delta by the matching fraction of it, so high-resolution wheels and touchpads zoom
     * by the amount they scrolled. Returns whether the event was consumed.
     */
    boolean scroll(double notches) {
        if (!ownsScroll()) {
            return false;
        }
        if (notches != 0 && Double.isFinite(notches)) {
            level = clamp(level * Math.pow(STEP, notches));
        }
        return true;
    }

    /** The wheel belongs to zoom only while zooming in or zoomed, and only with scroll zoom enabled. */
    boolean ownsScroll() {
        return requested && config.scroll();
    }

    /** Current zoom factor: 1 when idle, the shown level when fully zoomed, eased in log space between. */
    double factor() {
        if (progress <= 0) {
            return 1;
        }
        return Math.exp(Math.log(shownLevel) * config.curve().apply(progress));
    }

    /** Divides an already computed FOV by the zoom factor, never below {@link #MIN_FOV} nor above the input. */
    double fov(double vanillaFov) {
        double factor = factor();
        if (factor == 1) {
            appliedFactor = 1;
            return vanillaFov;
        }
        double zoomed = Math.min(vanillaFov, Math.max(MIN_FOV, vanillaFov / factor));
        appliedFactor = zoomed > 0 ? vanillaFov / zoomed : 1;
        return zoomed;
    }

    /** Multiplier for mouse turning: 1 / the factor actually applied to the FOV, or 1 when disabled by setting. */
    double sensitivityScale() {
        return config.reduceSensitivity() ? 1 / appliedFactor : 1;
    }

    double progress() {
        return progress;
    }

    double level() {
        return level;
    }

    double shownLevel() {
        return shownLevel;
    }

    private double clamp(double value) {
        return Math.clamp(value, config.lower(), config.upper());
    }
}
