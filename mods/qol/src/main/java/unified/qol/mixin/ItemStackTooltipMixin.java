package unified.qol.mixin;

import unified.qol.feature.foodhud.FoodTooltip;
import unified.qol.feature.shulkerpreview.ShulkerTooltip;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Food HUD Mixin M-3 (food-hud-v1.md §5.2) and Shulker Preview Mixin SP-2 (shulker-preview-v1.md §6): gives stacks
 * without a tooltip image a {@link ShulkerTooltip} (shulker boxes) or a {@link FoodTooltip} (food), which Fabric's
 * {@code ClientTooltipComponentCallback} turns into components. Stacks that already have an image keep it, and a stack
 * gets at most one of the two.
 */
@Mixin(ItemStack.class)
abstract class ItemStackTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void qolUnified$foodTooltip(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        Optional<TooltipComponent> vanilla = cir.getReturnValue();
        ItemStack stack = (ItemStack) (Object) this;
        Optional<TooltipComponent> result = ShulkerTooltip.forStack(stack, vanilla);
        if (result == vanilla) {
            result = FoodTooltip.forStack(stack, vanilla);
        }
        if (result != vanilla) {
            cir.setReturnValue(result);
        }
    }
}
