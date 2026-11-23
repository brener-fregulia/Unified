package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import java.util.Objects;

/**
 * A snapshot of one vanilla or modded key mapping, with its texts already translated. {@code input} is {@code null}
 * when the mapping is unbound; {@code defaultInput} is {@code null} when its declared default is unbound.
 */
public record Binding(String id, String name, String category, String inputLabel, InputId input,
        InputId defaultInput) {
    public Binding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        inputLabel = inputLabel == null ? "" : inputLabel;
    }

    public boolean unbound() {
        return input == null;
    }

    public boolean isDefault() {
        return Objects.equals(input, defaultInput);
    }
}
