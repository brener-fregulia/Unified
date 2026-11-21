package io.github.brener_fregulia.qolunified;

import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.config.ConfigurationService;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QolUnifiedClient implements ClientModInitializer {
    public static final String MOD_ID = "qol_unified";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CoreRuntime core;

    /** The running Core; available after client initialization. */
    public static CoreRuntime core() {
        if (core == null) {
            throw new IllegalStateException("QoL: Unified is not initialized");
        }
        return core;
    }

    @Override
    public void onInitializeClient() {
        // 1. Features are registered here explicitly. None exist yet.
        FeatureRegistry registry = FeatureRegistry.builder().build();
        // 2–5. Load configuration, resolve choices, initialize the lifecycle and start enabled features.
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json");
        // Fabric calls client initializers on the client (render) thread; it becomes the Core's owner thread.
        core = CoreRuntime.start(registry, new ConfigurationService(configFile, registry), Thread.currentThread());
        LOGGER.info("QoL: Unified initialized successfully ({} features).", registry.features().size());
    }
}
