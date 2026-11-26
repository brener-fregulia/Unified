package io.github.brener_fregulia.qolunified.mixin;

import io.github.brener_fregulia.qolunified.feature.shulkerpreview.ShulkerSlotDecorator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shulker Preview Mixin SP-1 (shulker-preview-v1.md §6.1): after vanilla draws a slot's count, bar and cooldown, adds
 * the shulker box's predominant-item icon and capacity bar. Vanilla's own decorations are untouched.
 */
@Mixin(GuiGraphicsExtractor.class)
abstract class ShulkerSlotDecorationsMixin {
    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"))
    private void qolUnified$shulkerDecorations(Font font, ItemStack stack, int x, int y, String countText,
            CallbackInfo ci) {
        ShulkerSlotDecorator.decorate((GuiGraphicsExtractor) (Object) this, stack, x, y);
    }
}
