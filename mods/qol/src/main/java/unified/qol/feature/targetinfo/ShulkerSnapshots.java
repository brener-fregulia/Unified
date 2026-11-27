package unified.qol.feature.targetinfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import unified.qol.feature.shulkerpreview.ShulkerContents;
import unified.qol.feature.shulkerpreview.ShulkerSummary;

/**
 * Last-seen contents of placed shulker boxes (target-info-v1.md §17). Minecraft 26.3 never sends a placed box's items
 * to clients (its block entity's update tag is empty), in singleplayer and multiplayer alike, so the only legitimate
 * source is the box's own menu while the player has it open. When the player uses a placed box and a shulker box
 * menu opens right after, its 27 container slots are copied when it closes.
 *
 * <ul>
 *   <li>Scope: this connection only (cleared on join and disconnect), keyed by dimension and position.</li>
 *   <li>Invalidation: any client-visible block change at the position drops the snapshot (box broken, replaced,
 *       recolored), so a different box is never shown with old contents.</li>
 *   <li>Never current: contents can change after closing (hoppers, other players); the overlay labels it "last
 *       seen". Memory only, bounded, never written to disk.</li>
 * </ul>
 * Render thread only. Nothing is recorded while Target Info is off.
 */
public final class ShulkerSnapshots {
    /** How long after using a box its menu may open and still be attributed to it (1 s; a server round trip). */
    static final int OPEN_WINDOW_TICKS = 20;
    private static final int MAX_SNAPSHOTS = 512;

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<Key, ShulkerSummary<ItemStack>> SNAPSHOTS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, ShulkerSummary<ItemStack>> eldest) {
            return size() > MAX_SNAPSHOTS;
        }
    };
    private static long tick;
    private static Key pendingUse;
    private static long pendingUseTick;
    private static boolean registered;

    private ShulkerSnapshots() {
    }

    static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick++);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> clear());
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && TargetInfoFeature.activeSettings() != null
                    && level.getBlockState(hit.getBlockPos()).getBlock() instanceof ShulkerBoxBlock) {
                pendingUse = new Key(level.dimension(), hit.getBlockPos().immutable());
                pendingUseTick = tick;
            }
            return InteractionResult.PASS; // observe only
        });
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof ShulkerBoxScreen box) {
                Key key = attribute(minecraft);
                if (key != null) {
                    ShulkerBoxMenu menu = box.getMenu();
                    ScreenEvents.remove(screen).register(s -> capture(minecraft, key, menu));
                }
            }
        });
    }

    /** The box this menu belongs to: the one used within the last second, still a shulker box; else none. */
    private static Key attribute(Minecraft minecraft) {
        Key key = pendingUse;
        pendingUse = null;
        if (key == null || tick - pendingUseTick > OPEN_WINDOW_TICKS || minecraft.level == null
                || minecraft.level.dimension() != key.dimension()
                || !(minecraft.level.getBlockState(key.pos()).getBlock() instanceof ShulkerBoxBlock)) {
            return null;
        }
        return key;
    }

    private static void capture(Minecraft minecraft, Key key, ShulkerBoxMenu menu) {
        if (minecraft.player == null || TargetInfoFeature.activeSettings() == null) {
            return;
        }
        Inventory inventory = minecraft.player.getInventory();
        List<ItemStack> items = new ArrayList<>(ShulkerSummary.SLOTS);
        for (Slot slot : menu.slots) {
            if (slot.container != inventory && items.size() < ShulkerSummary.SLOTS) {
                items.add(slot.getItem());
            }
        }
        if (items.size() != ShulkerSummary.SLOTS) {
            return; // not a standard box menu: record nothing rather than guess
        }
        SNAPSHOTS.put(key, ShulkerContents.summaryOf(items));
    }

    /** Called for every client block change (Mixin); a different block at a remembered position drops it. */
    public static void blockChanged(Level level, BlockPos pos, BlockState before, BlockState after) {
        if (before.getBlock() != after.getBlock() && !SNAPSHOTS.isEmpty()) {
            SNAPSHOTS.remove(new Key(level.dimension(), pos));
        }
    }

    /** The last-seen summary of the placed box at {@code pos}, or {@code null} when never seen (unknown). */
    static ShulkerSummary<ItemStack> lastSeen(Level level, BlockPos pos) {
        return SNAPSHOTS.get(new Key(level.dimension(), pos));
    }

    static void clear() {
        SNAPSHOTS.clear();
        pendingUse = null;
    }

    /** Snapshots held now; for development game tests. */
    public static int size() {
        return SNAPSHOTS.size();
    }
}
