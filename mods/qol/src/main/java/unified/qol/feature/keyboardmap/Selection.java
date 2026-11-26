package unified.qol.feature.keyboardmap;

import java.util.List;
import java.util.Objects;

/**
 * What the side panel shows (keyboard-map-ux-v1.md §0, §6): Explore (the list), an input card (the mappings on one
 * selected key or mouse button) or a binding card (one unbound action, so it can be assigned). Selection never writes
 * a mapping.
 */
public final class Selection {
    public enum Mode {
        EXPLORE,
        INPUT,
        BINDING
    }

    private Mode mode = Mode.EXPLORE;
    private InputId input;
    private String bindingId;

    public Mode mode() {
        return mode;
    }

    /** Clicking a key or mouse region: opens its card, or returns to Explore when it is already selected. */
    public void selectInput(InputId input) {
        if (mode == Mode.INPUT && Objects.equals(this.input, input)) {
            clear();
            return;
        }
        mode = input == null ? Mode.EXPLORE : Mode.INPUT;
        this.input = input;
        this.bindingId = null;
    }

    /**
     * Clicking a row: a bound action opens its input's card with the action in focus; an unbound action opens its own
     * card, where it can be assigned.
     */
    public void selectBinding(BindingCatalog catalog, String id) {
        Binding binding = catalog.binding(id).orElse(null);
        if (binding == null) {
            clear();
        } else if (binding.unbound()) {
            mode = Mode.BINDING;
            input = null;
            bindingId = id;
        } else {
            mode = Mode.INPUT;
            input = binding.input();
            bindingId = id;
        }
    }

    /** After a remap, the card follows the action to its new input (or to its own card when it became unbound). */
    public void follow(BindingCatalog catalog, String id) {
        selectBinding(catalog, id);
    }

    /** Back to Explore: the ✕ button, Escape, or typing in search. */
    public void clear() {
        mode = Mode.EXPLORE;
        input = null;
        bindingId = null;
    }

    /** The action in focus on the card, if any. */
    public String bindingId() {
        return bindingId;
    }

    /** The selected input (input card), or the focused action's input. */
    public InputId highlightedInput(BindingCatalog catalog) {
        return switch (mode) {
            case EXPLORE -> null;
            case INPUT -> input;
            case BINDING -> null;
        };
    }

    /** The actions on the card: all mappings on the selected input, or the one unbound action. */
    public List<Binding> highlightedBindings(BindingCatalog catalog) {
        return switch (mode) {
            case EXPLORE -> List.of();
            case INPUT -> catalog.on(input);
            case BINDING -> catalog.binding(bindingId).map(List::of).orElse(List.of());
        };
    }

    public boolean highlights(Binding binding, BindingCatalog catalog) {
        return highlightedBindings(catalog).stream().anyMatch(b -> b.id().equals(binding.id()));
    }
}
