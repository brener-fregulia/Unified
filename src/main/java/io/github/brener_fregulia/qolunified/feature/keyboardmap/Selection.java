package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import java.util.List;
import java.util.Objects;

/**
 * What is selected and what that highlights (keymap-v1.md I-1, I-2). Selecting an input highlights its mappings;
 * selecting a mapping highlights its input. At most one of the two is the primary selection.
 */
public final class Selection {
    private InputId input;
    private String bindingId;

    public void selectInput(InputId input) {
        this.input = Objects.equals(this.input, input) && bindingId == null ? null : input;
        this.bindingId = null;
    }

    public void selectBinding(String id) {
        this.bindingId = id;
        this.input = null;
    }

    public void clear() {
        input = null;
        bindingId = null;
    }

    public String bindingId() {
        return bindingId;
    }

    /** The highlighted input: the selected one, or the selected mapping's input. */
    public InputId highlightedInput(BindingCatalog catalog) {
        if (input != null) {
            return input;
        }
        return bindingId == null ? null : catalog.binding(bindingId).map(Binding::input).orElse(null);
    }

    /** The highlighted mappings: the selected one, or all mappings on the selected input. */
    public List<Binding> highlightedBindings(BindingCatalog catalog) {
        if (bindingId != null) {
            return catalog.binding(bindingId).map(List::of).orElse(List.of());
        }
        return catalog.on(input);
    }

    public boolean highlights(Binding binding, BindingCatalog catalog) {
        return highlightedBindings(catalog).stream().anyMatch(b -> b.id().equals(binding.id()));
    }
}
