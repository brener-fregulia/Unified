package io.github.brener_fregulia.qolunified.mixin;

import io.github.brener_fregulia.qolunified.feature.foodhud.FoodTooltip;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Food HUD Mixin M-3 (food-hud-v1.md §5.2): gives food stacks without a tooltip image a {@link FoodTooltip}, which
 * Fabric's {@code ClientTooltipComponentCallback} turns into the icon rows. Stacks that already have an image keep it.
 */
@Mixin(ItemStack.class)
abstract class ItemStackTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void qolUnified$foodTooltip(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        Optional<TooltipComponent> vanilla = cir.getReturnValue();
        Optional<TooltipComponent> result = FoodTooltip.forStack((ItemStack) (Object) this, vanilla);
        if (result != vanilla) {
            cir.setReturnValue(result);
        }
    }
}
