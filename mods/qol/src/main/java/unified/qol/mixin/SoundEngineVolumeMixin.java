package unified.qol.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import unified.qol.feature.soundcontrol.SoundControlHooks;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sound Control (sound-control-v1.md §7.2): multiplies vanilla's computed gain by the rule factor of the sound's
 * event id. The factor is 1 for sounds without a rule and while the feature is disabled, and never above 1. Pitch,
 * attenuation range, position, looping and scheduling are untouched.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineVolumeMixin {
    // Ticking sounds and category refreshes (including refreshCategoryVolume(MASTER) after a rule change).
    @ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"))
    private float qolUnified$scaleVolume(float volume, SoundInstance sound) {
        return volume * SoundControlHooks.factor(sound);
    }

    // The starting gain in play(), which also decides vanilla's "volume was zero" skip.
    @ModifyExpressionValue(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float qolUnified$scaleStartVolume(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * SoundControlHooks.factor(sound);
    }
}
