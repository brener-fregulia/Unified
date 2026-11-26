package unified.qol.mixin;

import java.util.List;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Inventory Mouse Tweaks (mouse-tweaks-v1.md §9): access to the slot under a point, to vanilla's item-specific
 * wheel actions (bundles), so vanilla keeps those, and to its drag-to-spread state, so a Shift + drag collection can
 * end it without a click. No injection.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenSlotsAccessor {
    @Invoker("getHoveredSlot")
    Slot qolUnified$slotAt(double x, double y);

    @Accessor("itemSlotMouseActions")
    List<ItemSlotMouseAction> qolUnified$itemSlotMouseActions();

    /** Ends vanilla's drag-to-spread after a Shift + drag collection the gesture handled; no click is sent. */
    @Accessor("isQuickCrafting")
    void qolUnified$setQuickCrafting(boolean quickCrafting);

    @Accessor("quickCraftSlots")
    java.util.Set<Slot> qolUnified$quickCraftSlots();
}
