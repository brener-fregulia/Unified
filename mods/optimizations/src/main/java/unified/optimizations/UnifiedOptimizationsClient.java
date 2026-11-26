package unified.optimizations;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified: Optimizations entrypoint (docs/optimizations/README.md). The foundation only starts and logs: no feature is
 * approved for implementation yet, so there is no feature registry, Mixin or configuration file. It never depends on
 * Unified: QoL.
 */
public final class UnifiedOptimizationsClient implements ClientModInitializer {
    public static final String MOD_ID = "unified_optimizations";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info(StartupReport.message(FabricLoader.getInstance().getConfigDir()));
    }
}
