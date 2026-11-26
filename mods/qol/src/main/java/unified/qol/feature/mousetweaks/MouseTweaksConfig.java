package unified.qol.feature.mousetweaks;

import unified.qol.core.SettingsSnapshot;

/** The settings as plain fields; a gesture captures one when it starts (mouse-tweaks-v1.md §8). */
record MouseTweaksConfig(boolean rmbDrag, boolean lmbCollect, boolean shiftDrag, boolean wheelTransfer,
        boolean wheelInverted, boolean lastToFirst) {

    static MouseTweaksConfig from(SettingsSnapshot s) {
        return new MouseTweaksConfig(s.getBoolean(MouseTweaksSettings.RMB_DRAG),
                s.getBoolean(MouseTweaksSettings.LMB_COLLECT), s.getBoolean(MouseTweaksSettings.SHIFT_DRAG),
                s.getBoolean(MouseTweaksSettings.WHEEL_TRANSFER),
                MouseTweaksSettings.INVERTED.equals(s.getEnum(MouseTweaksSettings.WHEEL_DIRECTION)),
                MouseTweaksSettings.LAST_TO_FIRST.equals(s.getEnum(MouseTweaksSettings.WHEEL_SEARCH_ORDER)));
    }

    /**
     * Turns a vertical wheel delta into a signed push amount: positive pushes out of the hovered stack, negative
     * pulls into it. Normal direction: scrolling down (negative delta) pushes.
     */
    double pushAmount(double vertical) {
        return wheelInverted ? vertical : -vertical;
    }
}
