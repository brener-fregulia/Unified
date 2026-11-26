package unified.qol.mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only: whether the engine has an audio device; vanilla plays nothing otherwise (sound-control-v1.md §6.5). */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("loaded")
    boolean qolUnified$loaded();
}
