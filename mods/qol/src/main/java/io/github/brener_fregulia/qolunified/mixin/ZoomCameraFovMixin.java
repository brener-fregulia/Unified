package io.github.brener_fregulia.qolunified.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.brener_fregulia.qolunified.feature.zoom.ZoomHooks;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Zoom (zoom-v1.md §6, Q-Z9): divides the world FOV after vanilla has applied the player's FOV option, FOV effects,
 * the spyglass, death and fluids. The hand keeps its separate HUD FOV. Panoramic captures are left alone.
 */
@Mixin(Camera.class)
abstract class ZoomCameraFovMixin {
    @Shadow
    public abstract boolean isPanoramicMode();

    @ModifyReturnValue(method = "calculateFov(F)F", at = @At("RETURN"))
    private float qolUnified$zoom(float fov) {
        return isPanoramicMode() ? fov : ZoomHooks.fov(fov);
    }
}
