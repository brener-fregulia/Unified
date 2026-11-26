package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KeyboardLayoutsTest {
    private static Set<Integer> scancodes(KeyboardLayout layout) {
        var set = new HashSet<Integer>();
        layout.keys().forEach(k -> assertTrue(set.add(k.scancode()), "duplicate scancode " + k.scancode()));
        return set;
    }

    @Test
    void keyCountsMatchTheSpecification() {
        assertEquals(104, KeyboardLayouts.US.keys().size());
        assertEquals(107, KeyboardLayouts.BR.keys().size());
    }

    @Test
    void noKeyRectanglesOverlap() {
        for (KeyboardLayout layout : List.of(KeyboardLayouts.US, KeyboardLayouts.BR)) {
            List<KeyCap> keys = layout.keys();
            for (int i = 0; i < keys.size(); i++) {
                for (int j = i + 1; j < keys.size(); j++) {
                    assertFalse(keys.get(i).overlaps(keys.get(j)),
                            layout.id() + ": " + keys.get(i).scancode() + " overlaps " + keys.get(j).scancode());
                }
            }
        }
    }

    @Test
    void usAnsiCoversTheStandardScancodes() {
        Set<Integer> us = scancodes(KeyboardLayouts.US);
        // Letters, digits, Enter..Space, punctuation (no Non-US #), Caps, F1–F12, PrtSc..arrows, numpad, Menu,
        // modifiers.
        IntStream.rangeClosed(4, 49).forEach(code -> assertTrue(us.contains(code), "missing " + code));
        IntStream.rangeClosed(51, 99).forEach(code -> assertTrue(us.contains(code), "missing " + code));
        IntStream.rangeClosed(224, 231).forEach(code -> assertTrue(us.contains(code), "missing " + code));
        assertTrue(us.contains(101));
        assertFalse(us.contains(50), "Non-US # is not on ANSI");
        assertFalse(us.contains(100), "Non-US backslash is not on ANSI");
        assertFalse(us.contains(135));
        assertFalse(us.contains(133));
    }

    @Test
    void abnt2HasItsExtraKeys() {
        Set<Integer> br = scancodes(KeyboardLayouts.BR);
        Set<Integer> us = scancodes(KeyboardLayouts.US);
        var added = new HashSet<>(br);
        added.removeAll(us);
        var removed = new HashSet<>(us);
        removed.removeAll(br);
        assertEquals(Set.of(100, 133, 135), added, "key left of Z, keypad comma, /? key");
        assertEquals(Set.of(), removed, "the ']' key reports backslash (49) on Windows and SDL");
        assertEquals(KeyboardLayouts.BR.key(52).orElseThrow().x() + 4, KeyboardLayouts.BR.key(49).orElseThrow().x(),
                "backslash scancode drawn at the end of the home row");
        assertTrue(br.contains(51), "Ç is the HID semicolon position, labeled by the OS");
    }

    @Test
    void abnt2PositionsAreWhereTheKeysArePrinted() {
        KeyboardLayout br = KeyboardLayouts.BR;
        KeyCap shift = br.key(225).orElseThrow();
        KeyCap extra = br.key(100).orElseThrow();
        KeyCap z = br.key(29).orElseThrow();
        assertEquals(shift.y(), extra.y());
        assertEquals(shift.x() + shift.width(), extra.x(), "extra key right of a short left Shift");
        assertEquals(extra.x() + extra.width(), z.x());
        KeyCap slash = br.key(135).orElseThrow();
        KeyCap rightShift = br.key(229).orElseThrow();
        assertEquals(slash.x() + slash.width(), rightShift.x(), "/? key left of right Shift");
        KeyCap cedilla = br.key(51).orElseThrow();
        assertEquals(br.key(15).orElseThrow().x() + 4, cedilla.x(), "Ç right of L");
        KeyCap enter = br.key(40).orElseThrow();
        assertEquals(8, enter.height(), "tall Enter over two rows");
        KeyCap comma = br.key(133).orElseThrow();
        assertEquals(KeyCap.Section.NUMPAD, comma.section());
        assertEquals(br.key(87).orElseThrow().y() + 4, comma.y(), "keypad comma under keypad plus");
    }

    @Test
    void sectionsCanBeShownAloneStartingAtTheLeft() {
        List<KeyCap> numpad = KeyboardLayouts.US.keys(Set.of(KeyCap.Section.NUMPAD));
        assertEquals(17, numpad.size());
        assertEquals(0, numpad.stream().mapToInt(KeyCap::x).min().orElseThrow());
        assertEquals(16, KeyboardLayout.width(numpad));
        assertEquals(60, KeyboardLayout.width(KeyboardLayouts.US.keys(Set.of(KeyCap.Section.MAIN))));
        assertEquals(96, KeyboardLayout.width(KeyboardLayouts.US.keys(KeyboardLayout.ALL)), "full-key gaps between groups");
        assertEquals(26, KeyboardLayout.height(KeyboardLayouts.US.keys(KeyboardLayout.ALL)));
    }

    @Test
    void layoutIdsResolveWithAnAnsiFallback() {
        assertSame(KeyboardLayouts.BR, KeyboardLayouts.byId("abnt2"));
        assertSame(KeyboardLayouts.US, KeyboardLayouts.byId("us_ansi"));
        assertSame(KeyboardLayouts.US, KeyboardLayouts.byId("unknown"));
        assertTrue(KeyboardLayouts.US.contains(InputId.key(4)));
        assertFalse(KeyboardLayouts.US.contains(InputId.mouse(4)), "mouse button 4 is not scancode 4");
    }
}
