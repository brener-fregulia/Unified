package io.github.brener_fregulia.qolunified.mixin;

import io.github.brener_fregulia.qolunified.feature.shulkerpreview.ShulkerPreviewSettings.Mode;
import io.github.brener_fregulia.qolunified.feature.shulkerpreview.ShulkerTooltip;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shulker Preview Mixin SP-3 (shulker-preview-v1.md §6.4): skips vanilla's "64 x Name / and N more" lines, which
 * come from the {@code minecraft:container} component, only for a shulker box whose preview is showing. Other
 * components, other containers and every other tooltip line (name, lore, advanced information) are unaffected.
 */
@Mixin(ItemStack.class)
abstract class ShulkerContainerTooltipMixin {
    @Inject(method = "addToTooltip(Lnet/minecraft/core/component/DataComponentType;"
            + "Lnet/minecraft/world/item/component/TooltipProvider$Getter;"
            + "Lnet/minecraft/world/item/Item$TooltipContext;"
            + "Lnet/minecraft/world/item/component/TooltipDisplay;"
            + "Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("HEAD"), cancellable = true)
    private <T> void qolUnified$skipDuplicateLines(DataComponentType<T> type, TooltipProvider.Getter<T> getter,
            Item.TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag,
            CallbackInfo ci) {
        if (type == DataComponents.CONTAINER && ShulkerTooltip.mode((ItemStack) (Object) this) != Mode.VANILLA) {
            ci.cancel();
        }
    }
}
