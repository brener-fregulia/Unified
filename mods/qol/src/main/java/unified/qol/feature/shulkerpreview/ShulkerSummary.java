package unified.qol.feature.shulkerpreview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a shulker box holds, summarized (shulker-preview-v1.md §5, §6). Pure and generic over the stack type, so it is
 * unit-tested without the game. Immutable once built.
 *
 * @param <S> the stack type (an {@code ItemStack} copy in the game)
 */
public final class ShulkerSummary<S> {
    public static final int SLOTS = 27;
    /** Most other kinds listed by the compact preview before "+N". */
    public static final int MAX_OTHER_KINDS = 9;
    /** Width of the vanilla item bar, in pixels. */
    public static final int BAR_WIDTH = 13;

    /**
     * One slot of the box.
     *
     * @param kind the grouping key (the item, ignoring components); {@code null} for an empty slot
     * @param stack the original stack, or {@code null} for an empty slot
     */
    public record Slot<S>(Object kind, S stack, int count, int maxStackSize) {
        public boolean empty() {
            return kind == null || stack == null || count <= 0;
        }
    }

    /**
     * One kind of item and its total.
     *
     * @param representative the first stack of this kind in slot order, used for its icon
     * @param firstSlot the lowest slot index holding this kind
     */
    public record Kind<S>(Object key, S representative, long total, int firstSlot) {
    }

    private final List<Slot<S>> slots;
    private final List<Kind<S>> kinds;
    private final int usedSlots;
    private final long totalItems;
    private final double fraction;

    private ShulkerSummary(List<Slot<S>> slots, List<Kind<S>> kinds, int usedSlots, long totalItems,
            double fraction) {
        this.slots = slots;
        this.kinds = kinds;
        this.usedSlots = usedSlots;
        this.totalItems = totalItems;
        this.fraction = fraction;
    }

    /** Summarizes up to {@link #SLOTS} slots in slot order; missing slots count as empty, extra ones are ignored. */
    public static <S> ShulkerSummary<S> of(List<Slot<S>> input) {
        Objects.requireNonNull(input, "input");
        List<Slot<S>> slots = new ArrayList<>(SLOTS);
        Map<Object, long[]> totals = new LinkedHashMap<>();
        Map<Object, Integer> first = new LinkedHashMap<>();
        int used = 0;
        long total = 0;
        double occupancy = 0.0;
        for (int i = 0; i < SLOTS; i++) {
            Slot<S> slot = i < input.size() ? input.get(i) : null;
            if (slot == null || slot.empty()) {
                slots.add(null);
                continue;
            }
            slots.add(slot);
            used++;
            total += slot.count();
            totals.computeIfAbsent(slot.kind(), k -> new long[1])[0] += slot.count();
            first.putIfAbsent(slot.kind(), i);
            // A stack with a broken max size counts as one full slot; oversized stacks never exceed one slot.
            double share = slot.maxStackSize() <= 0 ? 1.0 : (double) slot.count() / slot.maxStackSize();
            occupancy += Math.clamp(share, 0.0, 1.0);
        }
        List<Kind<S>> kinds = new ArrayList<>(totals.size());
        for (Map.Entry<Object, long[]> entry : totals.entrySet()) {
            int index = first.get(entry.getKey());
            kinds.add(new Kind<>(entry.getKey(), slots.get(index).stack(), entry.getValue()[0], index));
        }
        // Largest total first; on a tie the kind seen first in slot order (Q-SP2).
        kinds.sort(Comparator.comparingLong((Kind<S> k) -> -k.total()).thenComparingInt(Kind::firstSlot));
        return new ShulkerSummary<>(List.copyOf(nullsAllowed(slots)), List.copyOf(kinds), used, total,
                Math.clamp(occupancy / SLOTS, 0.0, 1.0));
    }

    // List.copyOf rejects nulls; keep empty slots as explicit placeholders.
    private static <S> List<Slot<S>> nullsAllowed(List<Slot<S>> slots) {
        List<Slot<S>> out = new ArrayList<>(slots.size());
        for (Slot<S> slot : slots) {
            out.add(slot == null ? new Slot<>(null, null, 0, 0) : slot);
        }
        return out;
    }

    public boolean isEmpty() {
        return usedSlots == 0;
    }

    /** The 27 slots in order; empty slots have {@link Slot#empty()} true. */
    public List<Slot<S>> slots() {
        return slots;
    }

    /** All kinds, largest total first. */
    public List<Kind<S>> kinds() {
        return kinds;
    }

    /** The predominant kind, or {@code null} when empty. */
    public Kind<S> predominant() {
        return kinds.isEmpty() ? null : kinds.getFirst();
    }

    /** The kinds after the predominant one that the compact preview lists (at most {@link #MAX_OTHER_KINDS}). */
    public List<Kind<S>> listedOthers() {
        return kinds.size() <= 1 ? List.of() : kinds.subList(1, Math.min(kinds.size(), 1 + MAX_OTHER_KINDS));
    }

    /** Kinds beyond the listed ones, shown as "+N". */
    public int hiddenKinds() {
        return Math.max(0, kinds.size() - 1 - MAX_OTHER_KINDS);
    }

    /** The original stack of the first non-empty slot (0 to 26, row by row), or {@code null} when empty. */
    public S firstOccupied() {
        for (Slot<S> slot : slots) {
            if (!slot.empty()) {
                return slot.stack();
            }
        }
        return null;
    }

    /**
     * The slot icon's stack for a {@link ShulkerPreviewSettings#SLOT_ICON_SOURCE} value: the first occupied slot
     * (default) or the predominant kind's representative; {@code null} when the box is empty.
     */
    public S iconStack(String source) {
        if (ShulkerPreviewSettings.PREDOMINANT.equals(source)) {
            Kind<S> main = predominant();
            return main == null ? null : main.representative();
        }
        return firstOccupied();
    }

    public int usedSlots() {
        return usedSlots;
    }

    public long totalItems() {
        return totalItems;
    }

    /** Approximate normalized storage occupancy, 0–1: Σ(count ÷ max stack size) ÷ 27. Not the used-slot ratio. */
    public double fraction() {
        return fraction;
    }

    public boolean full() {
        return fraction >= 1.0;
    }

    /** Filled width of the bundle-style bar, as vanilla computes the bundle's: min(1 + ⌊fraction × 12⌋, 13). */
    public int barWidth() {
        return Math.min(1 + (int) Math.floor(fraction * (BAR_WIDTH - 1)), BAR_WIDTH);
    }

    /** A count for a small icon: as is below 1000, else thousands with one truncated decimal ("1.7k"). */
    public static String shortCount(long count) {
        if (count < 1000) {
            return Long.toString(count);
        }
        long tenths = count / 100;
        return tenths % 10 == 0 ? (tenths / 10) + "k" : (tenths / 10) + "." + (tenths % 10) + "k";
    }
}
