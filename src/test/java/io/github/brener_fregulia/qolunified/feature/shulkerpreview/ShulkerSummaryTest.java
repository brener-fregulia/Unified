package io.github.brener_fregulia.qolunified.feature.shulkerpreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.feature.shulkerpreview.ShulkerSummary.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stacks are strings here: "kind#tag"; the kind is the part before '#', like an item ignoring components. */
class ShulkerSummaryTest {
    private static Slot<String> s(String stack, int count, int max) {
        return new Slot<>(stack.split("#")[0], stack, count, max);
    }

    @SafeVarargs
    private static List<Slot<String>> box(Slot<String>... slots) {
        return new ArrayList<>(Arrays.asList(slots));
    }

    private static List<Slot<String>> filled(int slots, Slot<String> each) {
        return new ArrayList<>(Collections.nCopies(slots, each));
    }

    @Test
    void emptyAndMalformedBoxesAreEmpty() {
        assertTrue(ShulkerSummary.of(List.<Slot<String>>of()).isEmpty());
        var nulls = ShulkerSummary.of(box(null, null, new Slot<>(null, null, 5, 64), s("stone", 0, 64)));
        assertTrue(nulls.isEmpty());
        assertNull(nulls.predominant());
        assertEquals(0.0, nulls.fraction());
        assertEquals(27, nulls.slots().size());
    }

    @Test
    void predominantIsTheLargestTotalAndTotalsSpanSlots() {
        var summary = ShulkerSummary.of(box(s("dirt", 64, 64), s("stone", 40, 64), s("stone", 40, 64), null,
                s("torch", 10, 64)));
        assertEquals("stone", summary.predominant().key());
        assertEquals(80, summary.predominant().total());
        assertEquals(1, summary.predominant().firstSlot());
        assertEquals(List.of("stone", "dirt", "torch"), summary.kinds().stream().map(k -> k.key()).toList());
        assertEquals(154, summary.totalItems());
        assertEquals(4, summary.usedSlots());
    }

    @Test
    void groupingIgnoresComponentsAndKeepsTheFirstOriginalStack() {
        var summary = ShulkerSummary.of(box(s("enchanted_book#sharpness", 1, 1), s("diamond", 1, 64),
                s("enchanted_book#mending", 1, 1), s("enchanted_book#unbreaking", 1, 1)));
        assertEquals(2, summary.kinds().size());
        assertEquals("enchanted_book", summary.predominant().key());
        assertEquals(3, summary.predominant().total());
        assertEquals("enchanted_book#sharpness", summary.predominant().representative());
    }

    @Test
    void tiesGoToTheKindSeenFirstInSlotOrder() {
        var summary = ShulkerSummary.of(box(null, s("gold", 32, 64), s("iron", 32, 64), s("gold#x", 0, 64)));
        assertEquals("gold", summary.predominant().key());
        var swapped = ShulkerSummary.of(box(s("iron", 16, 64), s("gold", 16, 64), s("iron", 16, 64),
                s("gold", 16, 64)));
        assertEquals("iron", swapped.predominant().key());
    }

    @Test
    void capacityUsesEachItemsMaxStackSize() {
        assertEquals(0.0, ShulkerSummary.of(box()).fraction());
        assertEquals(1.0 / 27, ShulkerSummary.of(box(s("stone", 64, 64))).fraction(), 1e-9, "≈3.7 %");
        assertEquals(1.0 / 27, ShulkerSummary.of(box(s("pearl", 16, 16))).fraction(), 1e-9);
        assertEquals(1.0 / 27, ShulkerSummary.of(box(s("sword", 1, 1))).fraction(), 1e-9);
        assertEquals(0.5 / 27, ShulkerSummary.of(box(s("pearl", 8, 16))).fraction(), 1e-9);
        var full64 = ShulkerSummary.of(filled(27, s("stone", 64, 64)));
        assertEquals(1.0, full64.fraction());
        assertTrue(full64.full());
        assertEquals(13, full64.barWidth());
        var unstackable = ShulkerSummary.of(filled(27, s("sword", 1, 1)));
        assertEquals(1.0, unstackable.fraction(), "27 unstackable items are full");
        // About half: every slot half full of 64-items, or half the slots full.
        var half = ShulkerSummary.of(filled(27, s("stone", 32, 64)));
        assertEquals(0.5, half.fraction(), 1e-9);
        assertFalse(half.full());
        assertEquals(7, half.barWidth(), "1 + floor(0.5 × 12)");
        var slotsHalf = ShulkerSummary.of(filled(27, s("stone", 64, 64)).subList(0, 14));
        assertEquals(14.0 / 27, slotsHalf.fraction(), 1e-9);
        assertEquals(1, ShulkerSummary.of(box(s("stone", 1, 64))).barWidth(), "a non-empty box shows one pixel");
    }

    @Test
    void malformedStacksNeverOverflowTheBar() {
        var oversized = ShulkerSummary.of(filled(27, s("stone", 500, 64)));
        assertEquals(1.0, oversized.fraction());
        var brokenMax = ShulkerSummary.of(box(s("odd", 3, 0)));
        assertEquals(1.0 / 27, brokenMax.fraction(), 1e-9, "counts as one full slot");
        var extra = new ArrayList<Slot<String>>(filled(40, s("stone", 64, 64)));
        assertEquals(27, ShulkerSummary.of(extra).usedSlots(), "slots beyond 27 are ignored");
    }

    @Test
    void compactListsUpToNineOtherKindsThenPlusN() {
        var slots = new ArrayList<Slot<String>>();
        for (int i = 0; i < 12; i++) {
            slots.add(s("k" + i, 64 - i, 64));
        }
        var summary = ShulkerSummary.of(slots);
        assertEquals("k0", summary.predominant().key());
        assertEquals(9, summary.listedOthers().size());
        assertEquals("k1", summary.listedOthers().getFirst().key());
        assertEquals(2, summary.hiddenKinds());
        var small = ShulkerSummary.of(box(s("a", 1, 64)));
        assertEquals(List.of(), small.listedOthers());
        assertEquals(0, small.hiddenKinds());
    }

    @Test
    void slotsKeepTheirExactPositions() {
        var summary = ShulkerSummary.of(box(null, null, s("a", 1, 64), null, s("b#named", 2, 64)));
        assertTrue(summary.slots().get(0).empty());
        assertEquals("a", summary.slots().get(2).stack());
        assertEquals("b#named", summary.slots().get(4).stack());
        assertTrue(summary.slots().get(26).empty());
    }

    @Test
    void firstOccupiedSkipsEmptySlotsButNeverPicksAnotherWhenTheBoxIsEmpty() {
        var sandAt5 = new ArrayList<Slot<String>>(Collections.nCopies(5, null));
        sandAt5.add(s("sand", 3, 64));
        assertEquals("sand", ShulkerSummary.of(sandAt5).iconStack(ShulkerPreviewSettings.FIRST_OCCUPIED),
                "slot 0 empty, sand in slot 5");
        var stoneFirst = new ArrayList<Slot<String>>(sandAt5);
        stoneFirst.set(0, s("stone#named", 1, 64));
        assertEquals("stone#named", ShulkerSummary.of(stoneFirst).iconStack(ShulkerPreviewSettings.FIRST_OCCUPIED),
                "the original stack of slot 0");
        assertNull(ShulkerSummary.of(box()).iconStack(ShulkerPreviewSettings.FIRST_OCCUPIED), "empty box");
        assertNull(ShulkerSummary.of(box()).iconStack(ShulkerPreviewSettings.PREDOMINANT), "empty box");
    }

    @Test
    void eachSourceShowsItsOwnItem() {
        // Stone is first, sand is predominant.
        var summary = ShulkerSummary.of(box(s("stone", 1, 64), null, s("sand", 64, 64), s("sand#x", 10, 64)));
        assertEquals("stone", summary.iconStack(ShulkerPreviewSettings.FIRST_OCCUPIED));
        assertEquals("sand", summary.iconStack(ShulkerPreviewSettings.PREDOMINANT));
        assertEquals("stone", summary.iconStack("unknown_value"), "anything else falls back to the default");
        // Predominant ties keep the V1 rule: the first in slot order.
        var tie = ShulkerSummary.of(box(null, s("gold", 8, 64), s("iron", 8, 64)));
        assertEquals("gold", tie.iconStack(ShulkerPreviewSettings.PREDOMINANT));
        // The compact summary is unchanged: it always leads with the predominant kind.
        assertEquals("sand", summary.predominant().key());
    }

    @Test
    void shortCounts() {
        assertEquals("999", ShulkerSummary.shortCount(999));
        assertEquals("1k", ShulkerSummary.shortCount(1000));
        assertEquals("1.7k", ShulkerSummary.shortCount(1728));
    }

    @Test
    void modes() {
        assertSame(ShulkerPreviewSettings.Mode.COMPACT, ShulkerPreviewSettings.mode(true, true, false));
        assertSame(ShulkerPreviewSettings.Mode.FULL, ShulkerPreviewSettings.mode(true, true, true));
        assertSame(ShulkerPreviewSettings.Mode.COMPACT, ShulkerPreviewSettings.mode(true, false, true));
        assertSame(ShulkerPreviewSettings.Mode.VANILLA, ShulkerPreviewSettings.mode(false, true, false));
        assertSame(ShulkerPreviewSettings.Mode.FULL, ShulkerPreviewSettings.mode(false, true, true));
        assertSame(ShulkerPreviewSettings.Mode.VANILLA, ShulkerPreviewSettings.mode(false, false, true));
    }
}
