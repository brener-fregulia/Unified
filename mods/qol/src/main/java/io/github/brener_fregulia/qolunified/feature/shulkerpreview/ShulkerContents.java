package io.github.brener_fregulia.qolunified.feature.shulkerpreview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Reads a shulker box item's contents from its {@code minecraft:container} component (shulker-preview-v1.md §4, §6).
 * Unknown contents (no component, unopened loot, or hidden by the item's own tooltip display) give {@code null}, never
 * a guess. Summaries are cached by component identity: components are immutable and a changed box carries a new one,
 * so a cached summary can never be stale.
 */
public final class ShulkerContents {
    private static final int CACHE_SIZE = 256;

    /** Identity key: equal-content components from different stacks may share a summary, but lookups stay cheap. */
    private record Key(ItemContainerContents contents) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.contents == contents;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(contents);
        }
    }

    // Render thread only.
    private static final Map<Key, ShulkerSummary<ItemStack>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, ShulkerSummary<ItemStack>> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static long builds;

    private ShulkerContents() {
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * The summary of a shulker box with known, non-empty contents, or {@code null} (not a shulker box, empty, or
     * contents not known to the client).
     */
    public static ShulkerSummary<ItemStack> summary(ItemStack stack) {
        if (!isShulkerBox(stack) || stack.has(DataComponents.CONTAINER_LOOT)) {
            return null;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null || contents == ItemContainerContents.EMPTY) {
            return null;
        }
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (!display.shows(DataComponents.CONTAINER)) {
            return null; // the item itself asks to hide its contents
        }
        Key key = new Key(contents);
        ShulkerSummary<ItemStack> cached = CACHE.get(key);
        if (cached == null) {
            cached = build(contents);
            CACHE.put(key, cached);
            builds++;
        }
        return cached.isEmpty() ? null : cached;
    }

    private static ShulkerSummary<ItemStack> build(ItemContainerContents contents) {
        NonNullList<ItemStack> items = NonNullList.withSize(ShulkerSummary.SLOTS, ItemStack.EMPTY);
        try {
            contents.copyInto(items); // copies: the cache never holds a live, mutable stack
        } catch (RuntimeException e) {
            return ShulkerSummary.of(List.of()); // malformed component: treated as unknown
        }
        List<ShulkerSummary.Slot<ItemStack>> slots = new ArrayList<>(ShulkerSummary.SLOTS);
        for (ItemStack item : items) {
            slots.add(item.isEmpty() ? null
                    : new ShulkerSummary.Slot<>(item.getItem(), item, item.getCount(), item.getMaxStackSize()));
        }
        return ShulkerSummary.of(slots);
    }

    /** The box's dye color as an opaque RGB, or a neutral purple for the undyed box. */
    public static int accent(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock box) {
            DyeColor color = box.getColor();
            if (color != null) {
                return 0xFF000000 | color.getTextureDiffuseColor();
            }
        }
        return 0xFF8E648E;
    }

    /** Summaries built so far; for development game tests (cache hits do not count). */
    public static long builds() {
        return builds;
    }

    static void clearCache() {
        CACHE.clear();
    }
}
