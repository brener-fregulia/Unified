package unified.optimizations.mixin;

import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fast IP Ping (fast-ip-ping-v1.md §5): vanilla's own blocklist check, applied unchanged on the fast path. */
@Mixin(ServerNameResolver.class)
public interface ServerNameResolverAccessor {
    @Accessor("addressCheck")
    AddressCheck unifiedOptimizations$addressCheck();
}
