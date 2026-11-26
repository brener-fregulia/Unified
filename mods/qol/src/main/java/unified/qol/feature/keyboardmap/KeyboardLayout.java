package unified.qol.feature.keyboardmap;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A hand-authored physical keyboard drawing (keymap-v1.md KL-1). Geometry only; no labels, no bindings. */
public record KeyboardLayout(String id, List<KeyCap> keys) {
    public KeyboardLayout {
        keys = List.copyOf(keys);
    }

    public Optional<KeyCap> key(int scancode) {
        return keys.stream().filter(k -> k.scancode() == scancode).findFirst();
    }

    public boolean contains(InputId input) {
        return input.kind() == InputId.Kind.KEYBOARD && key(input.code()).isPresent();
    }

    /** Keys of the given sections, moved so that the leftmost one starts at x = 0. */
    public List<KeyCap> keys(Set<KeyCap.Section> sections) {
        List<KeyCap> selected = keys.stream().filter(k -> sections.contains(k.section())).toList();
        int left = selected.stream().mapToInt(KeyCap::x).min().orElse(0);
        return selected.stream().map(k -> new KeyCap(k.scancode(), k.x() - left, k.y(), k.width(), k.height(),
                k.fixedLabel(), k.section())).toList();
    }

    public static int width(List<KeyCap> keys) {
        return keys.stream().mapToInt(k -> k.x() + k.width()).max().orElse(0);
    }

    public static int height(List<KeyCap> keys) {
        return keys.stream().mapToInt(k -> k.y() + k.height()).max().orElse(0);
    }

    public static final Set<KeyCap.Section> ALL = EnumSet.allOf(KeyCap.Section.class);
}
