package unified.qol.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unified.qol.feature.targetinfo.ShulkerSnapshots;

/**
 * Target Info (target-info-v1.md §17): every client-side block change reaches {@code sendBlockUpdated}; a change at a
 * position with a last-seen shulker box snapshot drops it. Observes only.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelBlockChangeMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void qolUnified$dropShulkerSnapshot(BlockPos pos, BlockState before, BlockState after, int flags,
            CallbackInfo ci) {
        ShulkerSnapshots.blockChanged((ClientLevel) (Object) this, pos, before, after);
    }
}
