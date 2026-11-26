package unified.optimizations.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unified.optimizations.fastquit.PendingSaves;

/**
 * FastQuit (fastquit-v1.md §6): every world storage access (open, create, edit, delete, back up, Realms upload) waits
 * for a world still saving in the background, so a world is never opened before its previous shutdown finished and
 * two integrated servers never run at once. Free when nothing is pending.
 */
@Mixin(LevelStorageSource.class)
abstract class LevelStorageSourceMixin {
    @Inject(method = {"createAccess", "validateAndCreateAccess"}, at = @At("HEAD"))
    private void unifiedOptimizations$waitForPendingSave(String levelId,
            CallbackInfoReturnable<LevelStorageSource.LevelStorageAccess> cir) {
        if (PendingSaves.hasPending()) {
            PendingSaves.awaitAll(Minecraft.getInstance());
        }
    }
}
