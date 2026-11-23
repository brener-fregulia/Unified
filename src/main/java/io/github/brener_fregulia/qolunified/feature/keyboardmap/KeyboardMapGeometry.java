package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyCap.Section;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Responsive arrangement of the keyboard map (keyboard-map-ux-v1.md §0, §7), in GUI pixels. Fallback order:
 * (1) whole keyboard with the mouse beside it, (2) mouse below the keyboard, (3) main block on one page and
 * navigation, numpad and mouse under "More keys", (4) controls and actions as tabs. Keys are never under 12 pixels
 * (3 pixels per quarter key). Pure, so every window size can be checked in unit tests.
 */
public record KeyboardMapGeometry(int margin, int toolbarY, int contentTop, int contentBottom, int bottomBarY,
        int keyboardX, int keyboardWidth, int panelX, int panelWidth, int unit, Arrangement arrangement,
        boolean tabbed) {
    public static final int MARGIN = 6;
    public static final int BAR = 20;
    public static final int MIN_UNIT = 3;
    public static final int PREFERRED_UNIT = 4;
    public static final int MAX_UNIT = 9;
    /** Gap between the keyboard and the mouse, in quarter-key units (one key). */
    public static final int MOUSE_GAP = 4;

    public enum Arrangement {
        MOUSE_BESIDE,
        MOUSE_BELOW,
        SPLIT
    }

    private static final Set<Section> MAIN = EnumSet.of(Section.MAIN);
    private static final Set<Section> OTHERS = EnumSet.of(Section.NAVIGATION, Section.NUMPAD);

    public static KeyboardMapGeometry plan(int width, int height, KeyboardLayout layout) {
        int top = MARGIN + BAR + MARGIN;
        int bottomBar = height - MARGIN - BAR;
        int bottom = bottomBar - 4;
        int panelWidth = Math.clamp(width / 4, 130, 220);
        int keyboardWidth = width - panelWidth - 3 * MARGIN;
        int areaHeight = bottom - top;
        boolean tabbed = false;
        if (keyboardWidth < 60 * MIN_UNIT) {
            tabbed = true;
            keyboardWidth = width - 2 * MARGIN;
            panelWidth = keyboardWidth;
        }
        int allWidth = KeyboardLayout.width(layout.keys(KeyboardLayout.ALL));
        int allHeight = KeyboardLayout.height(layout.keys(KeyboardLayout.ALL));
        int[][] sizes = {
            {allWidth + MOUSE_GAP + MouseLayout.WIDTH, Math.max(allHeight, MouseLayout.HEIGHT)},
            {Math.max(allWidth, MouseLayout.WIDTH), allHeight + MOUSE_GAP + MouseLayout.HEIGHT},
            {Math.max(KeyboardLayout.width(layout.keys(MAIN)), othersWidth(layout)),
                Math.max(allHeight, MouseLayout.HEIGHT)}
        };
        Arrangement chosen = null;
        int unit = MIN_UNIT;
        for (int wanted : new int[] {PREFERRED_UNIT, MIN_UNIT}) {
            for (Arrangement arrangement : Arrangement.values()) {
                int[] size = sizes[arrangement.ordinal()];
                int u = Math.min(keyboardWidth / size[0], areaHeight / size[1]);
                if (u >= wanted) {
                    chosen = arrangement;
                    unit = Math.min(u, MAX_UNIT);
                    break;
                }
            }
            if (chosen != null) {
                break;
            }
        }
        if (chosen == null) {
            chosen = Arrangement.SPLIT;
        }
        int panelX = tabbed ? MARGIN : width - MARGIN - panelWidth;
        return new KeyboardMapGeometry(MARGIN, MARGIN, top, bottom, bottomBar, MARGIN, keyboardWidth, panelX,
                panelWidth, unit, chosen, tabbed);
    }

    private static int othersWidth(KeyboardLayout layout) {
        return KeyboardLayout.width(layout.keys(OTHERS)) + MOUSE_GAP + MouseLayout.WIDTH;
    }

    public boolean split() {
        return arrangement == Arrangement.SPLIT;
    }

    /** The keyboard sections drawn: all of them, or one page of the split. */
    public Set<Section> visibleSections(boolean moreKeys) {
        if (!split()) {
            return KeyboardLayout.ALL;
        }
        return moreKeys ? OTHERS : MAIN;
    }

    /** Whether the mouse is drawn: always, except on the main page of a split keyboard. */
    public boolean mouseVisible(boolean moreKeys) {
        return !split() || moreKeys;
    }

    /** Whether the mouse sits beside the drawn keys (else below them). */
    public boolean mouseBeside() {
        return arrangement != Arrangement.MOUSE_BELOW;
    }
}
