package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyCap.Section;
import java.util.ArrayList;
import java.util.List;

/**
 * The two V1 layouts (keymap-v1.md §5), written from the USB-HID usage table and standard keyboard geometry. Units are
 * quarter keys; rows are 4 units high, with a half-key gap under the function row. Scancodes are SDL's, which are
 * USB-HID usages (F-2).
 */
public final class KeyboardLayouts {
    public static final String US_ANSI = "us_ansi";
    public static final String ABNT2 = "abnt2";

    private static final int ROW_F = 0;
    private static final int ROW_1 = 6;
    private static final int ROW_2 = 10;
    private static final int ROW_3 = 14;
    private static final int ROW_4 = 18;
    private static final int ROW_5 = 22;
    private static final int NAV_X = 62;
    private static final int NUM_X = 76;

    public static final KeyboardLayout US = new KeyboardLayout(US_ANSI, build(false));
    public static final KeyboardLayout BR = new KeyboardLayout(ABNT2, build(true));

    private KeyboardLayouts() {
    }

    /**
     * Short, language-neutral keycap names used only when the OS name does not fit the drawn key (keymap-v1.md KL-2).
     * The OS name stays the label everywhere else (list, labels, status line).
     */
    private static final java.util.Map<Integer, String> SHORT = java.util.Map.ofEntries(
            java.util.Map.entry(41, "Esc"), java.util.Map.entry(42, "Bksp"), java.util.Map.entry(43, "Tab"),
            java.util.Map.entry(57, "Caps"), java.util.Map.entry(40, "Enter"), java.util.Map.entry(44, "Space"),
            java.util.Map.entry(225, "Shift"), java.util.Map.entry(229, "Shift"),
            java.util.Map.entry(224, "Ctrl"), java.util.Map.entry(228, "Ctrl"),
            java.util.Map.entry(226, "Alt"), java.util.Map.entry(230, "Alt"),
            java.util.Map.entry(227, "Super"), java.util.Map.entry(231, "Super"), java.util.Map.entry(101, "Menu"),
            java.util.Map.entry(70, "PrtSc"), java.util.Map.entry(71, "ScrLk"), java.util.Map.entry(72, "Pause"),
            java.util.Map.entry(73, "Ins"), java.util.Map.entry(74, "Home"), java.util.Map.entry(75, "PgUp"),
            java.util.Map.entry(76, "Del"), java.util.Map.entry(77, "End"), java.util.Map.entry(78, "PgDn"),
            java.util.Map.entry(83, "Num"), java.util.Map.entry(88, "Enter"), java.util.Map.entry(99, "."),
            java.util.Map.entry(133, ","));

    /** The short keycap name for {@code scancode}, or {@code null}. */
    public static String shortLabel(int scancode) {
        return SHORT.get(scancode);
    }

    public static KeyboardLayout byId(String id) {
        return ABNT2.equals(id) ? BR : US;
    }

    private static List<KeyCap> build(boolean abnt2) {
        var keys = new ArrayList<KeyCap>();
        // Function row: Esc, F1–F4, F5–F8, F9–F12.
        main(keys, 41, 0, ROW_F, 4);
        for (int i = 0; i < 12; i++) {
            main(keys, 58 + i, 8 + i * 4 + (i / 4) * 2, ROW_F, 4);
        }
        // Number row: grave, 1–9, 0, minus, equals, Backspace.
        main(keys, 53, 0, ROW_1, 4);
        for (int i = 0; i < 10; i++) {
            main(keys, 30 + i, 4 + i * 4, ROW_1, 4);
        }
        main(keys, 45, 44, ROW_1, 4);
        main(keys, 46, 48, ROW_1, 4);
        main(keys, 42, 52, ROW_1, 8);
        // Top letter row: Tab, Q–P, left and right bracket.
        main(keys, 43, 0, ROW_2, 6);
        int[] top = {20, 26, 8, 21, 23, 28, 24, 12, 18, 19};
        for (int i = 0; i < top.length; i++) {
            main(keys, top[i], 6 + i * 4, ROW_2, 4);
        }
        main(keys, 47, 46, ROW_2, 4);
        main(keys, 48, 50, ROW_2, 4);
        // Home row: Caps Lock, A–L, semicolon (Ç on ABNT2), apostrophe (~ on ABNT2).
        main(keys, 57, 0, ROW_3, 7);
        int[] home = {4, 22, 7, 9, 10, 11, 13, 14, 15};
        for (int i = 0; i < home.length; i++) {
            main(keys, home[i], 7 + i * 4, ROW_3, 4);
        }
        main(keys, 51, 43, ROW_3, 4);
        main(keys, 52, 47, ROW_3, 4);
        if (abnt2) {
            // ISO-style: the key at the end of the home row (']' on ABNT2) and a tall Enter over two rows. That key is
            // HID "Non-US #" (50) on paper, but Windows and SDL report it as backslash (49), as verified on an ABNT2
            // system, so 49 is drawn there.
            main(keys, 49, 51, ROW_3, 4);
            keys.add(new KeyCap(40, 55, ROW_2, 5, 8, null, Section.MAIN));
            // Short left Shift, Non-US backslash, Z–M, comma, period, semicolon key (HID slash), International1.
            main(keys, 225, 0, ROW_4, 5);
            main(keys, 100, 5, ROW_4, 4);
        } else {
            main(keys, 49, 54, ROW_2, 6);
            main(keys, 40, 51, ROW_3, 9);
            main(keys, 225, 0, ROW_4, 9);
        }
        int[] bottom = {29, 27, 6, 25, 5, 17, 16, 54, 55, 56};
        for (int i = 0; i < bottom.length; i++) {
            main(keys, bottom[i], 9 + i * 4, ROW_4, 4);
        }
        if (abnt2) {
            main(keys, 135, 49, ROW_4, 4);
            main(keys, 229, 53, ROW_4, 7);
        } else {
            main(keys, 229, 49, ROW_4, 11);
        }
        // Bottom row: Ctrl, GUI, Alt, Space, Alt, GUI, Menu (Application), Ctrl.
        main(keys, 224, 0, ROW_5, 5);
        main(keys, 227, 5, ROW_5, 5);
        main(keys, 226, 10, ROW_5, 5);
        main(keys, 44, 15, ROW_5, 25);
        main(keys, 230, 40, ROW_5, 5);
        main(keys, 231, 45, ROW_5, 5);
        main(keys, 101, 50, ROW_5, 5);
        main(keys, 228, 55, ROW_5, 5);

        // Navigation cluster.
        nav(keys, 70, 0, ROW_F, null);
        nav(keys, 71, 1, ROW_F, null);
        nav(keys, 72, 2, ROW_F, null);
        nav(keys, 73, 0, ROW_1, null);
        nav(keys, 74, 1, ROW_1, null);
        nav(keys, 75, 2, ROW_1, null);
        nav(keys, 76, 0, ROW_2, null);
        nav(keys, 77, 1, ROW_2, null);
        nav(keys, 78, 2, ROW_2, null);
        nav(keys, 82, 1, ROW_4, "↑");
        nav(keys, 80, 0, ROW_5, "←");
        nav(keys, 81, 1, ROW_5, "↓");
        nav(keys, 79, 2, ROW_5, "→");

        // Numpad. ABNT2 splits the tall plus into plus and keypad comma (HID 133).
        num(keys, 83, 0, ROW_1, 4, 4, null);
        num(keys, 84, 1, ROW_1, 4, 4, "/");
        num(keys, 85, 2, ROW_1, 4, 4, "*");
        num(keys, 86, 3, ROW_1, 4, 4, "-");
        int[][] digits = {{95, 96, 97}, {92, 93, 94}, {89, 90, 91}};
        String[][] labels = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                num(keys, digits[r][c], c, ROW_2 + r * 4, 4, 4, labels[r][c]);
            }
        }
        if (abnt2) {
            num(keys, 87, 3, ROW_2, 4, 4, "+");
            num(keys, 133, 3, ROW_3, 4, 4, null);
        } else {
            num(keys, 87, 3, ROW_2, 4, 8, "+");
        }
        num(keys, 88, 3, ROW_4, 4, 8, null);
        num(keys, 98, 0, ROW_5, 8, 4, "0");
        num(keys, 99, 2, ROW_5, 4, 4, null);
        return keys;
    }

    private static void main(List<KeyCap> keys, int scancode, int x, int y, int width) {
        keys.add(new KeyCap(scancode, x, y, width, 4, null, Section.MAIN));
    }

    private static void nav(List<KeyCap> keys, int scancode, int column, int y, String label) {
        keys.add(new KeyCap(scancode, NAV_X + column * 4, y, 4, 4, label, Section.NAVIGATION));
    }

    private static void num(List<KeyCap> keys, int scancode, int column, int y, int width, int height, String label) {
        keys.add(new KeyCap(scancode, NUM_X + column * 4, y, width, height, label, Section.NUMPAD));
    }
}
