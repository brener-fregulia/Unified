package io.github.brener_fregulia.qolunified.feature.zoom;

import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;

/** The zoom settings as plain fields, built once per settings change so frames never look values up by name. */
record ZoomConfig(boolean toggle, int initial, int min, int max, boolean scroll, int inMs, int outMs,
        Curve curve, boolean reduceSensitivity) {

    enum Curve {
        SMOOTH, LINEAR, INSTANT;

        /** Eased progress for p in [0, 1]; the same shape both ways, so a reversal never jumps. */
        double apply(double p) {
            return switch (this) {
                case SMOOTH -> {
                    double q = 1 - p;
                    yield 1 - q * q * q; // ease-out cubic
                }
                case LINEAR -> p;
                case INSTANT -> p > 0 ? 1 : 0;
            };
        }
    }

    static final ZoomConfig DEFAULTS = from(SettingsSnapshot.defaults(ZoomSettings.DESCRIPTOR));

    static ZoomConfig from(SettingsSnapshot s) {
        return new ZoomConfig(ZoomSettings.MODE_TOGGLE.equals(s.getEnum(ZoomSettings.MODE)),
                s.getInt(ZoomSettings.INITIAL), s.getInt(ZoomSettings.MIN), s.getInt(ZoomSettings.MAX),
                s.getBoolean(ZoomSettings.SCROLL), s.getInt(ZoomSettings.IN_MS), s.getInt(ZoomSettings.OUT_MS),
                switch (s.getEnum(ZoomSettings.TRANSITION)) {
                    case ZoomSettings.LINEAR -> Curve.LINEAR;
                    case ZoomSettings.INSTANT -> Curve.INSTANT;
                    default -> Curve.SMOOTH;
                },
                s.getBoolean(ZoomSettings.REDUCE_SENSITIVITY));
    }

    /** Lower scroll bound; stored settings always satisfy min ≤ initial ≤ max (ZoomSettings.BOUNDS). */
    double lower() {
        return Math.min(min, initial);
    }

    double upper() {
        return Math.max(max, initial);
    }
}
