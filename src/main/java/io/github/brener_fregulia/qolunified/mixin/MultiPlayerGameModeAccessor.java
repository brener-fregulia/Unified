package io.github.brener_fregulia.qolunified.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Target Info Mixin TI-1 (target-info-v1.md §0, §5.3): read-only access to the client's own block-breaking
 * prediction, for a smooth progress bar. No setter and no injection: mining behavior is unchanged.
 */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyProgress")
    float qolUnified$destroyProgress();

    @Accessor("destroyBlockPos")
    BlockPos qolUnified$destroyBlockPos();
}
