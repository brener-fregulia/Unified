package io.github.brener_fregulia.qolunified.feature.keyboardmap;

/**
 * A physical input, as Minecraft 26.3 stores it in a key binding: an SDL keyboard scancode or a mouse button
 * (keymap-v1.md F-2, F-5). Keyboard and mouse codes are separate identities, so keyboard scancode 4 and mouse button
 * 4 are never the same input.
 */
public record InputId(Kind kind, int code) {
    public enum Kind {
        KEYBOARD,
        MOUSE
    }

    public static InputId key(int scancode) {
        return new InputId(Kind.KEYBOARD, scancode);
    }

    public static InputId mouse(int button) {
        return new InputId(Kind.MOUSE, button);
    }
}
