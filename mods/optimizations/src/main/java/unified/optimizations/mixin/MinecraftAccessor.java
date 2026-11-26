package unified.optimizations.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** FastQuit (fastquit-v1.md §6): draws frames while waiting for a save, the way vanilla's own save wait does. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("renderFrame")
    void unifiedOptimizations$renderFrame(boolean advanceGameTime);

    @Accessor("sdlEventHandler")
    SDLEventHandler unifiedOptimizations$sdlEventHandler();
}
