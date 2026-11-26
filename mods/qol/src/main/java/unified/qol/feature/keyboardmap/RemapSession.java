package unified.qol.feature.keyboardmap;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The remapping state machine (keymap-v1.md §0). Input is captured only after an explicit {@link #begin}; nothing
 * else can change a mapping. A new input that would overlap other mappings waits for {@link #confirm()}.
 */
public final class RemapSession {
    /** Writes to the real mappings; the Minecraft implementation rebuilds the lookup and saves the options. */
    public interface BindingStore {
        /** Binds {@code id} to {@code input}, or unbinds it when {@code input} is {@code null}. */
        void assign(String id, InputId input);

        /** Restores the declared default. */
        void reset(String id);
    }

    public enum State {
        IDLE,
        CAPTURING,
        CONFIRMING
    }

    public enum Outcome {
        APPLIED,
        NEEDS_CONFIRMATION,
        CANCELED,
        IGNORED
    }

    private final Supplier<BindingCatalog> catalog;
    private final BindingStore store;
    private State state = State.IDLE;
    private String target;
    private InputId pending;
    private List<Binding> pendingOverlaps = List.of();

    public RemapSession(Supplier<BindingCatalog> catalog, BindingStore store) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.store = Objects.requireNonNull(store, "store");
    }

    public State state() {
        return state;
    }

    public String target() {
        return target;
    }

    public InputId pending() {
        return pending;
    }

    public List<Binding> pendingOverlaps() {
        return pendingOverlaps;
    }

    /** Starts waiting for the next input for mapping {@code id}. */
    public void begin(String id) {
        if (catalog.get().binding(id).isEmpty()) {
            return;
        }
        state = State.CAPTURING;
        target = id;
        pending = null;
        pendingOverlaps = List.of();
    }

    /** The next captured input. Ignored unless capturing. */
    public Outcome offer(InputId input) {
        if (state != State.CAPTURING || input == null) {
            return Outcome.IGNORED;
        }
        Binding binding = catalog.get().binding(target).orElse(null);
        if (binding == null) {
            return cancel();
        }
        if (input.equals(binding.input())) {
            idle();
            return Outcome.APPLIED;
        }
        List<Binding> overlaps = catalog.get().overlapsIfBound(target, input);
        if (!overlaps.isEmpty()) {
            state = State.CONFIRMING;
            pending = input;
            pendingOverlaps = overlaps;
            return Outcome.NEEDS_CONFIRMATION;
        }
        store.assign(target, input);
        idle();
        return Outcome.APPLIED;
    }

    /** Applies the pending input despite the overlap. */
    public Outcome confirm() {
        if (state != State.CONFIRMING) {
            return Outcome.IGNORED;
        }
        store.assign(target, pending);
        idle();
        return Outcome.APPLIED;
    }

    /** Escape, or the Cancel button: nothing changes. */
    public Outcome cancel() {
        if (state == State.IDLE) {
            return Outcome.IGNORED;
        }
        idle();
        return Outcome.CANCELED;
    }

    public void clear(String id) {
        if (state == State.IDLE && catalog.get().binding(id).filter(b -> !b.unbound()).isPresent()) {
            store.assign(id, null);
        }
    }

    public void reset(String id) {
        if (state == State.IDLE && catalog.get().binding(id).filter(b -> !b.isDefault()).isPresent()) {
            store.reset(id);
        }
    }

    private void idle() {
        state = State.IDLE;
        target = null;
        pending = null;
        pendingOverlaps = List.of();
    }
}
