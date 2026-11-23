package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyCap.Section;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Responsive arrangement of the keyboard map (keymap-v1.md §12), in GUI pixels. The keyboard is shrunk only down to
 * 3 pixels per quarter key (keys 12 pixels high); below that, sections are split, and as a last resort the list
 * becomes a tab. Pure, so it can be checked for every window size.
 */
public record KeyboardMapGeometry(int margin, int toolbarY, int contentTop, int contentBottom, int bottomBarY,
        int keyboardX, int keyboardWidth, int panelX, int panelWidth, int unit, Set<Section> sections,
        boolean split, boolean tabbed) {
    public static final int MARGIN = 6;
    public static final int BAR = 20;
    public static final int MOUSE_ROW = 16;
    public static final int MIN_UNIT = 3;
    public static final int PREFERRED_UNIT = 4;
    public static final int MAX_UNIT = 9;

    private static final List<Set<Section>> CANDIDATES = List.of(
            EnumSet.allOf(Section.class),
            EnumSet.of(Section.MAIN, Section.NAVIGATION),
            EnumSet.of(Section.MAIN));

    public static KeyboardMapGeometry plan(int width, int height, KeyboardLayout layout) {
        int top = MARGIN + BAR + MARGIN;
        int bottomBar = height - MARGIN - BAR;
        int bottom = bottomBar - MARGIN - 10;
        int panelWidth = Math.clamp(width * 3 / 10, 130, 230);
        int keyboardWidth = width - panelWidth - 3 * MARGIN;
        int keyboardHeight = bottom - top - MOUSE_ROW;
        boolean tabbed = false;
        if (keyboardWidth < 60 * MIN_UNIT) {
            // Too narrow for a side panel: keyboard and list share the full width as tabs.
            tabbed = true;
            keyboardWidth = width - 2 * MARGIN;
            panelWidth = keyboardWidth;
        }
        Set<Section> sections = null;
        int unit = 0;
        for (int wanted : new int[] {PREFERRED_UNIT, MIN_UNIT}) {
            for (Set<Section> candidate : CANDIDATES) {
                List<KeyCap> keys = layout.keys(candidate);
                int u = Math.min(keyboardWidth / KeyboardLayout.width(keys), keyboardHeight / KeyboardLayout.height(keys));
                if (u >= wanted) {
                    sections = candidate;
                    unit = Math.min(u, MAX_UNIT);
                    break;
                }
            }
            if (sections != null) {
                break;
            }
        }
        if (sections == null) {
            sections = EnumSet.of(Section.MAIN);
            unit = MIN_UNIT;
        }
        int panelX = tabbed ? MARGIN : width - MARGIN - panelWidth;
        return new KeyboardMapGeometry(MARGIN, MARGIN, top, bottom, bottomBar, MARGIN, keyboardWidth, panelX,
                panelWidth, unit, sections, sections.size() < Section.values().length, tabbed);
    }

    /** The sections drawn now: the main set, or the rest when the player switched to "More keys". */
    public Set<Section> visibleSections(boolean showOthers) {
        if (!split || !showOthers) {
            return sections;
        }
        Set<Section> others = EnumSet.complementOf(EnumSet.copyOf(sections));
        return others.isEmpty() ? sections : others;
    }
}
