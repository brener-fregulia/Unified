package io.github.brener_fregulia.qolunified.mixin;

import io.github.brener_fregulia.qolunified.feature.zoom.ZoomHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zoom (zoom-v1.md §7, §8, Q-Z9). Scroll: injected at vanilla's in-world wheel handling, after the screen branch has
 * returned, so menus, inventories and creative tabs never reach it; zoom takes the event only while it owns the
 * wheel. Turning: the final yaw and pitch deltas are scaled just before {@code LocalPlayer.turn}, after vanilla's
 * sensitivity, spyglass and Smooth Camera handling; the sensitivity option itself is never written.
 */
@Mixin(MouseHandler.class)
abstract class ZoomMouseMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll(JDD)V", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"))
    private void qolUnified$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomHooks.scroll(vertical, minecraft.options.discreteMouseScroll().get())) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "turnPlayer(D)V", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private double qolUnified$zoomYaw(double yaw) {
        return yaw * ZoomHooks.sensitivity();
    }

    @ModifyArg(method = "turnPlayer(D)V", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private double qolUnified$zoomPitch(double pitch) {
        return pitch * ZoomHooks.sensitivity();
    }
}
