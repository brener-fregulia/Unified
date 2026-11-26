package unified.optimizations;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unified.optimizations.core.Optimizations;

/**
 * Unified: Optimizations entrypoint (docs/optimizations/README.md): loads the configuration. Features act through
 * their Mixins, which check {@link Optimizations#isActive} each time. It
 * never depends on Unified: QoL.
 */
public final class UnifiedOptimizationsClient implements ClientModInitializer {
    public static final String MOD_ID = "unified_optimizations";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        Optimizations.start(OptimizationsPaths.configFile(loader.getConfigDir()), loader::isModLoaded);
        LOGGER.info(StartupReport.message(loader.getConfigDir(), Optimizations.activeCount()));
    }
}
