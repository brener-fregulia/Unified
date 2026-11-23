package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import java.util.List;
import java.util.Optional;

/**
 * A generic mouse drawing (keyboard-map-ux-v1.md §5), in the keyboard's quarter-key units, with no brand shape.
 * Button numbers are Minecraft 26.3's {@code InputConstants.MOUSE_BUTTON_*}: left 1, middle 2, right 3, side 4 and 5.
 * The wheel region is only middle click; wheel scrolling is not a key mapping in 26.3 (MD-6).
 */
public final class MouseLayout {
    public static final int LEFT = 1;
    public static final int MIDDLE = 2;
    public static final int RIGHT = 3;
    public static final int BACK = 4;
    public static final int FORWARD = 5;

    /** One clickable region: a mouse button and its rectangle. */
    public record Region(int button, int x, int y, int width, int height) {
        public InputId input() {
            return InputId.mouse(button);
        }

        boolean contains(double ux, double uy) {
            return ux >= x && ux < x + width && uy >= y && uy < y + height;
        }

        boolean overlaps(Region o) {
            return x < o.x + o.width && o.x < x + width && y < o.y + o.height && o.y < y + height;
        }
    }

    /** The body outline (not an input): buttons sit on its top; the side buttons stick out on its left flank. */
    public static final int BODY_X = 1;
    public static final int BODY_Y = 0;
    public static final int BODY_WIDTH = 8;
    public static final int BODY_HEIGHT = 12;
    public static final int WIDTH = BODY_X + BODY_WIDTH;
    public static final int HEIGHT = BODY_HEIGHT;

    public static final List<Region> REGIONS = List.of(
            new Region(LEFT, 1, 0, 3, 5),
            new Region(MIDDLE, 4, 1, 2, 3),
            new Region(RIGHT, 6, 0, 3, 5),
            new Region(FORWARD, 0, 6, 1, 2),
            new Region(BACK, 0, 9, 1, 2));

    private MouseLayout() {
    }

    /** The region under a point given in quarter-key units relative to the mouse's top-left corner. */
    public static Optional<Region> regionAt(double ux, double uy) {
        return REGIONS.stream().filter(r -> r.contains(ux, uy)).findFirst();
    }

    public static Optional<Region> region(int button) {
        return REGIONS.stream().filter(r -> r.button() == button).findFirst();
    }

    /** Whether a point is on the body (including buttons), for drawing and for ignoring clicks on the shell. */
    public static boolean onBody(double ux, double uy) {
        return ux >= BODY_X && ux < BODY_X + BODY_WIDTH && uy >= BODY_Y && uy < BODY_Y + BODY_HEIGHT;
    }
}
