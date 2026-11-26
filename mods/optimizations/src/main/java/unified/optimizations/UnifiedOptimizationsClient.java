package unified.optimizations;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unified.optimizations.core.Optimizations;
import unified.optimizations.fastquit.PendingSaves;
import unified.optimizations.mixin.MinecraftAccessor;

/**
 * Unified: Optimizations entrypoint (docs/optimizations/README.md): loads the configuration and wires the FastQuit
 * tick. Fast IP Ping and FastQuit act through their Mixins, which check {@link Optimizations#isActive} each time. It
 * never depends on Unified: QoL.
 */
public final class UnifiedOptimizationsClient implements ClientModInitializer {
    public static final String MOD_ID = "unified_optimizations";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        Optimizations.start(OptimizationsPaths.configFile(loader.getConfigDir()), loader::isModLoaded);
        PendingSaves.Rendering.setRenderer(minecraft -> {
            RenderSystem.pumpEvents(((MinecraftAccessor) minecraft).unifiedOptimizations$sdlEventHandler());
            ((MinecraftAccessor) minecraft).unifiedOptimizations$renderFrame(false);
            return kotlin.Unit.INSTANCE;
        });
        ClientTickEvents.END_CLIENT_TICK.register(PendingSaves::tick);
        LOGGER.info(StartupReport.message(loader.getConfigDir(), Optimizations.activeCount()));
    }
}
