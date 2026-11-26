package unified.qol.feature.keyboardmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.feature.keyboardmap.KeyCap.Section;
import unified.qol.feature.keyboardmap.KeyboardMapGeometry.Arrangement;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyboardMapGeometryTest {
    /** GUI sizes of 854×480, 1280×720, 1920×1080 and 3440×1440 windows at GUI scales 1–4. */
    private static final int[][] SIZES = {{854, 480}, {427, 240}, {284, 160}, {640, 360}, {1280, 720}, {960, 540},
        {480, 270}, {1920, 1080}, {3440, 1440}, {1720, 720}, {860, 360}, {320, 240}};

    /** Width and height, in units, of what one page draws. */
    private static int[] drawn(KeyboardMapGeometry g, KeyboardLayout layout, boolean moreKeys) {
        List<KeyCap> keys = layout.keys(g.visibleSections(moreKeys));
        int w = KeyboardLayout.width(keys);
        int h = KeyboardLayout.height(keys);
        if (g.mouseVisible(moreKeys)) {
            if (g.mouseBeside()) {
                w += KeyboardMapGeometry.MOUSE_GAP + MouseLayout.WIDTH;
                h = Math.max(h, MouseLayout.HEIGHT);
            } else {
                w = Math.max(w, MouseLayout.WIDTH);
                h += KeyboardMapGeometry.MOUSE_GAP + MouseLayout.HEIGHT;
            }
        }
        return new int[] {w, h};
    }

    @Test
    void keysAreNeverSmallerThanTwelvePixelsAndNothingSpillsOut() {
        for (KeyboardLayout layout : List.of(KeyboardLayouts.US, KeyboardLayouts.BR)) {
            for (int[] size : SIZES) {
                var g = KeyboardMapGeometry.plan(size[0], size[1], layout);
                String where = layout.id() + " " + size[0] + "x" + size[1];
                assertTrue(g.unit() >= KeyboardMapGeometry.MIN_UNIT, where);
                if (g.contentBottom() - g.contentTop() < 26 * KeyboardMapGeometry.MIN_UNIT) {
                    continue; // below the supported minimum window
                }
                for (boolean more : new boolean[] {false, true}) {
                    int[] d = drawn(g, layout, more);
                    assertTrue(d[0] * g.unit() <= g.keyboardWidth(), where + " width");
                    assertTrue(d[1] * g.unit() <= g.contentBottom() - g.contentTop(), where + " height");
                }
                if (!g.tabbed()) {
                    assertTrue(g.keyboardX() + g.keyboardWidth() < g.panelX(), where + ": panel beside");
                }
            }
        }
    }

    @Test
    void fallbackOrderIsBesideBelowSplitTabs() {
        assertEquals(Arrangement.MOUSE_BESIDE, KeyboardMapGeometry.plan(960, 540, KeyboardLayouts.US).arrangement());
        assertEquals(Arrangement.MOUSE_BESIDE, KeyboardMapGeometry.plan(640, 360, KeyboardLayouts.US).arrangement());
        assertEquals(Arrangement.MOUSE_BELOW, KeyboardMapGeometry.plan(560, 315, KeyboardLayouts.US).arrangement());
        var small = KeyboardMapGeometry.plan(427, 240, KeyboardLayouts.US);
        assertEquals(Arrangement.SPLIT, small.arrangement());
        assertFalse(small.tabbed());
        assertTrue(KeyboardMapGeometry.plan(284, 160, KeyboardLayouts.US).tabbed());
    }

    @Test
    void largeWindowsShowEverythingWithTheKeyboardDominant() {
        var g = KeyboardMapGeometry.plan(960, 540, KeyboardLayouts.US);
        assertEquals(EnumSet.allOf(Section.class), g.visibleSections(false));
        assertTrue(g.mouseVisible(false));
        assertTrue(g.keyboardWidth() > 2 * g.panelWidth(), "keyboard is the dominant element");
    }

    @Test
    void splitPagesCoverEveryKeyAndPutTheMouseWithTheOthers() {
        var g = KeyboardMapGeometry.plan(427, 240, KeyboardLayouts.US);
        assertTrue(g.unit() >= KeyboardMapGeometry.PREFERRED_UNIT);
        assertEquals(EnumSet.of(Section.MAIN), g.visibleSections(false));
        assertFalse(g.mouseVisible(false));
        assertEquals(EnumSet.of(Section.NAVIGATION, Section.NUMPAD), g.visibleSections(true));
        assertTrue(g.mouseVisible(true));
    }

    @Test
    void ultrawideKeysStayBounded() {
        assertEquals(KeyboardMapGeometry.MAX_UNIT, KeyboardMapGeometry.plan(3440, 1440, KeyboardLayouts.US).unit());
    }
}
