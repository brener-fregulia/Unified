package io.github.brener_fregulia.qolunified.feature.keyboardmap;

/**
 * One drawn key: its SDL scancode and its rectangle in quarter-key units (a standard key is 4 × 4). The label is not
 * stored here; it comes from the operating system at runtime, except for {@code fixedLabel}, a language-neutral
 * symbol used where the OS name does not fit a keycap (numpad and arrows).
 */
public record KeyCap(int scancode, int x, int y, int width, int height, String fixedLabel, Section section) {
    public enum Section {
        MAIN,
        NAVIGATION,
        NUMPAD
    }

    public KeyCap {
        if (width <= 0 || height <= 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("Bad key geometry for scancode " + scancode);
        }
    }

    public boolean overlaps(KeyCap other) {
        return x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height;
    }
}
