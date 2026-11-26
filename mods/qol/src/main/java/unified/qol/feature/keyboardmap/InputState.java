package unified.qol.feature.keyboardmap;

/**
 * V1 classification of an input (keymap-v1.md §0): no mapping, exactly one, or two or more. Two or more is an
 * overlap whatever the actions do; Minecraft runs every mapping bound to a pressed input (F-6).
 */
public enum InputState {
    UNBOUND,
    SINGLE,
    OVERLAP;

    static InputState of(int count) {
        return count == 0 ? UNBOUND : count == 1 ? SINGLE : OVERLAP;
    }
}
