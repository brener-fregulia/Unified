package io.github.brener_fregulia.qolunified.mixin;

import java.util.List;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Inventory Mouse Tweaks (mouse-tweaks-v1.md §9): read-only access to the slot under a point and to vanilla's
 * item-specific wheel actions (bundles), so vanilla keeps those. No injection.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenSlotsAccessor {
    @Invoker("getHoveredSlot")
    Slot qolUnified$slotAt(double x, double y);

    @Accessor("itemSlotMouseActions")
    List<ItemSlotMouseAction> qolUnified$itemSlotMouseActions();
}
