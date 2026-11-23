package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyCap.Section;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyboardMapGeometryTest {
    /** GUI sizes of 854×480, 1280×720, 1920×1080 and 3440×1440 windows at GUI scales 1–4. */
    private static final int[][] SIZES = {{854, 480}, {427, 240}, {284, 160}, {640, 360}, {1280, 720}, {960, 540},
        {480, 270}, {1920, 1080}, {640, 360}, {3440, 1440}, {860, 360}, {320, 240}};

    @Test
    void keysAreNeverSmallerThanTwelvePixelsAndAlwaysFit() {
        for (KeyboardLayout layout : List.of(KeyboardLayouts.US, KeyboardLayouts.BR)) {
            for (int[] size : SIZES) {
                var g = KeyboardMapGeometry.plan(size[0], size[1], layout);
                String where = layout.id() + " " + size[0] + "x" + size[1];
                assertTrue(g.unit() >= KeyboardMapGeometry.MIN_UNIT, where);
                for (boolean others : new boolean[] {false, true}) {
                    List<KeyCap> keys = layout.keys(g.visibleSections(others));
                    if (g.contentBottom() - g.contentTop() >= 26 * 3 + KeyboardMapGeometry.MOUSE_ROW) {
                        assertTrue(KeyboardLayout.width(keys) * g.unit() <= g.keyboardWidth(), where + " width");
                        assertTrue(KeyboardLayout.height(keys) * g.unit() + KeyboardMapGeometry.MOUSE_ROW
                                <= g.contentBottom() - g.contentTop(), where + " height");
                    }
                }
            }
        }
    }

    @Test
    void largeWindowsShowTheWholeKeyboardBesideTheList() {
        var g = KeyboardMapGeometry.plan(960, 540, KeyboardLayouts.US);
        assertEquals(EnumSet.allOf(Section.class), g.sections());
        assertFalse(g.split());
        assertFalse(g.tabbed());
        assertTrue(g.keyboardX() + g.keyboardWidth() < g.panelX(), "panel to the right of the keyboard");
        assertTrue(g.keyboardWidth() > g.panelWidth(), "keyboard is the dominant element");
    }

    @Test
    void smallWindowsSplitSectionsInsteadOfShrinkingKeys() {
        var g = KeyboardMapGeometry.plan(427, 240, KeyboardLayouts.US);
        assertTrue(g.split());
        assertTrue(g.unit() >= KeyboardMapGeometry.PREFERRED_UNIT);
        assertTrue(g.visibleSections(false).contains(Section.MAIN));
        assertFalse(g.visibleSections(true).contains(Section.MAIN), "More keys shows the rest");
        var all = EnumSet.copyOf(g.visibleSections(false));
        all.addAll(g.visibleSections(true));
        assertEquals(EnumSet.allOf(Section.class), all, "every key is reachable");
    }

    @Test
    void veryNarrowWindowsUseTabs() {
        var g = KeyboardMapGeometry.plan(284, 160, KeyboardLayouts.US);
        assertTrue(g.tabbed());
        assertEquals(g.keyboardX(), g.panelX());
    }

    @Test
    void ultrawideKeysStayBounded() {
        var g = KeyboardMapGeometry.plan(3440, 1440, KeyboardLayouts.US);
        assertEquals(KeyboardMapGeometry.MAX_UNIT, g.unit());
    }
}
