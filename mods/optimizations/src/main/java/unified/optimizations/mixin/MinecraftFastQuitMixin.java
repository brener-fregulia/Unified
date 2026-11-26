package unified.optimizations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unified.optimizations.core.OptimizationFeatures;
import unified.optimizations.core.Optimizations;
import unified.optimizations.fastquit.PendingSaves;

/**
 * FastQuit (fastquit-v1.md §6). After vanilla has called {@code halt(false)} on the integrated server, it renders
 * "Saving world" until {@code isShutdown()}; while FastQuit is active the shutdown is tracked instead and the loop
 * ends at once. Closing the game first waits for a tracked shutdown. Multiplayer has no integrated server and is not
 * affected.
 */
@Mixin(Minecraft.class)
abstract class MinecraftFastQuitMixin {
    @WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"))
    private boolean unifiedOptimizations$trackShutdown(IntegratedServer server, Operation<Boolean> original) {
        if (Optimizations.isActive(OptimizationFeatures.FAST_QUIT.getId())
                && PendingSaves.track((Minecraft) (Object) this, server)) {
            return true;
        }
        return original.call(server);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void unifiedOptimizations$waitBeforeClose(CallbackInfo ci) {
        PendingSaves.awaitAll((Minecraft) (Object) this);
    }
}
