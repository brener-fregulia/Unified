package io.github.brener_fregulia.qolunified.mixin;

import io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Food HUD Mixin M-2 (food-hud-v1.md §5.2): records where {@code Hud.extractHearts} drew each heart container,
 * including shake and regeneration offsets, and draws the health preview on top when the method returns.
 * Arguments are only read, never changed. {@code ModifyArgs} is used because {@code Hud.HeartType} is not
 * accessible from here.
 */
@Mixin(Hud.class)
abstract class HudHeartsMixin {
    @Inject(method = "extractHearts", at = @At("HEAD"))
    private void qolUnified$beginHearts(GuiGraphicsExtractor graphics, Player player, int left, int top,
            int rowHeight, int regenerationIndex, float maxHealth, int health, int displayHealth, int absorption,
            boolean blinking, CallbackInfo ci) {
        FoodHudRenderer.beginHearts();
    }

    // The first extractHeart call per heart draws its container at the final position.
    @ModifyArgs(method = "extractHearts", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/Hud;extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Hud$HeartType;IIZZZ)V"))
    private void qolUnified$recordHeart(Args args) {
        FoodHudRenderer.recordHeart(args.<Integer>get(2), args.<Integer>get(3));
    }

    @Inject(method = "extractHearts", at = @At("RETURN"))
    private void qolUnified$endHearts(GuiGraphicsExtractor graphics, Player player, int left, int top,
            int rowHeight, int regenerationIndex, float maxHealth, int health, int displayHealth, int absorption,
            boolean blinking, CallbackInfo ci) {
        FoodHudRenderer.endHearts(graphics, player, left, top, rowHeight);
    }
}
