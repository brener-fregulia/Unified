package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** An immutable snapshot of all mappings, indexed by input (keymap-v1.md P-1). Built on open and after changes. */
public final class BindingCatalog {
    private final List<Binding> bindings;
    private final Map<InputId, List<Binding>> byInput = new LinkedHashMap<>();
    private final List<String> categories;

    public BindingCatalog(List<Binding> bindings) {
        this.bindings = List.copyOf(bindings);
        var categories = new LinkedHashSet<String>();
        for (Binding binding : this.bindings) {
            categories.add(binding.category());
            if (!binding.unbound()) {
                byInput.computeIfAbsent(binding.input(), i -> new ArrayList<>()).add(binding);
            }
        }
        byInput.replaceAll((input, list) -> List.copyOf(list));
        this.categories = List.copyOf(categories);
    }

    public List<Binding> bindings() {
        return bindings;
    }

    /** Category labels in first-seen order (vanilla's sorted order when built from Minecraft). */
    public List<String> categories() {
        return categories;
    }

    public Optional<Binding> binding(String id) {
        return bindings.stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** Mappings bound to {@code input}, in catalog order. */
    public List<Binding> on(InputId input) {
        return input == null ? List.of() : byInput.getOrDefault(input, List.of());
    }

    public InputState state(InputId input) {
        return InputState.of(on(input).size());
    }

    /** State of a binding's own input; {@link InputState#UNBOUND} when it is unbound. */
    public InputState state(Binding binding) {
        return binding.unbound() ? InputState.UNBOUND : state(binding.input());
    }

    /** Every bound input, keyboard and mouse. */
    public List<InputId> inputs() {
        return List.copyOf(byInput.keySet());
    }

    /** The other mappings already on {@code input}: what binding {@code id} to it would overlap with. */
    public List<Binding> overlapsIfBound(String id, InputId input) {
        return on(input).stream().filter(b -> !b.id().equals(id)).toList();
    }
}
