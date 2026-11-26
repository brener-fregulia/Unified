package unified.optimizations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import unified.optimizations.core.OptimizationFeatures;
import unified.optimizations.core.Optimizations;
import unified.optimizations.fastipping.FastIpPing;

/**
 * Fast IP Ping (fast-ip-ping-v1.md §5): only the multiplayer server-list status ping. Joining and Direct Connect use
 * {@code ConnectScreen} and are not touched. Off, or for any host that is not a non-public IP literal, vanilla runs.
 */
@Mixin(ServerStatusPinger.class)
abstract class ServerStatusPingerMixin {
    @WrapOperation(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/"
            + "resolver/ServerNameResolver;resolveAddress(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)"
            + "Ljava/util/Optional;"))
    private Optional<ResolvedServerAddress> unifiedOptimizations$fastResolve(ServerNameResolver resolver,
            ServerAddress address, Operation<Optional<ResolvedServerAddress>> original) {
        if (Optimizations.isActive(OptimizationFeatures.FAST_IP_PING.getId())) {
            Optional<ResolvedServerAddress> fast = FastIpPing.resolve(address,
                    ((ServerNameResolverAccessor) resolver).unifiedOptimizations$addressCheck());
            if (fast != null) {
                return fast;
            }
        }
        return original.call(resolver, address);
    }
}
