package io.github.brener_fregulia.qolunified.feature.zoom;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.BooleanSetting;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.EnumSetting;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.IntegerSetting;
import io.github.brener_fregulia.qolunified.core.SettingsRule;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import java.util.List;
import java.util.Optional;

/** Zoom descriptor, setting keys and the bounds rule (zoom-v1.md §0, §10). Free of Minecraft types. */
public final class ZoomSettings {
    public static final String ID = "zoom";
    /** Fabric mod ids, read from the published 26.3 JARs' fabric.mod.json (zoom-v1.md §2.1). */
    public static final String ZOOMIFY = "zoomify";
    public static final String OK_ZOOMER = "ok_zoomer";
    public static final String LOGICAL_ZOOM = "logical_zoom";

    public static final String MODE = "zoom_mode";
    public static final String INITIAL = "initial_magnification";
    public static final String MIN = "min_magnification";
    public static final String MAX = "max_magnification";
    public static final String SCROLL = "scroll_zoom";
    public static final String IN_MS = "zoom_in_ms";
    public static final String OUT_MS = "zoom_out_ms";
    public static final String TRANSITION = "transition";
    public static final String REDUCE_SENSITIVITY = "reduce_sensitivity";

    public static final String MODE_HOLD = "hold";
    public static final String MODE_TOGGLE = "toggle";
    public static final String SMOOTH = "smooth";
    public static final String LINEAR = "linear";
    public static final String INSTANT = "instant";

    private static final String PREFIX = "qol_unified.feature.zoom.";
    public static final String ERROR_INITIAL = PREFIX + "error.initial_outside_bounds";
    public static final String ERROR_MIN = PREFIX + "error.min_above_initial";
    public static final String ERROR_MAX = PREFIX + "error.max_below_initial";

    /**
     * Minimum ≤ initial ≤ maximum, which also keeps minimum ≤ maximum. The message names the value to change
     * first, so the player can always reach any coherent combination one field at a time.
     */
    static final SettingsRule BOUNDS = ZoomSettings::checkBounds;

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.CONTROLS, true,
            ApplyMode.LIVE,
            List.of(
                    new EnumSetting(MODE, MODE_HOLD, List.of(MODE_HOLD, MODE_TOGGLE), ApplyMode.LIVE),
                    new IntegerSetting(INITIAL, 4, 2, 10, ApplyMode.LIVE),
                    new IntegerSetting(MIN, 2, 1, 10, ApplyMode.LIVE),
                    new IntegerSetting(MAX, 16, 2, 50, ApplyMode.LIVE),
                    new BooleanSetting(SCROLL, true, ApplyMode.LIVE),
                    new IntegerSetting(IN_MS, 250, 0, 2000, ApplyMode.LIVE),
                    new IntegerSetting(OUT_MS, 150, 0, 2000, ApplyMode.LIVE),
                    new EnumSetting(TRANSITION, SMOOTH, List.of(SMOOTH, LINEAR, INSTANT), ApplyMode.LIVE),
                    new BooleanSetting(REDUCE_SENSITIVITY, true, ApplyMode.LIVE)),
            List.of(new ConflictDescriptor(ZOOMIFY, PREFIX + "conflict.zoomify"),
                    new ConflictDescriptor(OK_ZOOMER, PREFIX + "conflict.ok_zoomer"),
                    new ConflictDescriptor(LOGICAL_ZOOM, PREFIX + "conflict.logical_zoom")),
            BOUNDS);

    private ZoomSettings() {
    }

    private static Optional<SettingsRule.Violation> checkBounds(SettingsSnapshot s, String changed) {
        int initial = s.getInt(INITIAL);
        int min = s.getInt(MIN);
        int max = s.getInt(MAX);
        if (min <= initial && initial <= max) {
            return Optional.empty();
        }
        if (MIN.equals(changed)) {
            return Optional.of(new SettingsRule.Violation(ERROR_MIN, List.of(initial)));
        }
        if (MAX.equals(changed)) {
            return Optional.of(new SettingsRule.Violation(ERROR_MAX, List.of(initial)));
        }
        return Optional.of(new SettingsRule.Violation(ERROR_INITIAL, List.of(min, max)));
    }
}
