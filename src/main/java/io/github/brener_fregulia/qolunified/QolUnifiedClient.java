package io.github.brener_fregulia.qolunified;

import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapHooks;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.config.ConfigurationService;
import io.github.brener_fregulia.qolunified.ui.screen.SettingsKeyBinding;
import io.github.brener_fregulia.qolunified.core.Feature;
import java.nio.file.Path;
import java.util.List;
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
        // 1. Features are registered explicitly in Features. Sample features exist only in development.
        FabricLoader loader = FabricLoader.getInstance();
        List<Feature> development = loader.isDevelopmentEnvironment()
                ? loader.getEntrypoints(DevelopmentFeatures.ENTRYPOINT, DevelopmentFeatures.class).stream()
                        .flatMap(source -> source.features().stream()).toList()
                : List.of();
        if (!development.isEmpty()) {
            LOGGER.warn("Development environment: registering {} sample features", development.size());
        }
        FeatureRegistry registry = Features.createRegistry(development);
        // 2–6. Load configuration, detect installed conflicts, resolve choices, initialize the lifecycle
        // and start enabled features.
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json");
        // Fabric calls client initializers on the client (render) thread; it becomes the Core's owner thread.
        core = CoreRuntime.start(registry, new ConfigurationService(configFile, registry), loader::isModLoaded,
                Thread.currentThread());
        SettingsKeyBinding.register();
        // Fabric only accepts key mappings during client start, so this one exists even while the feature is off;
        // it opens nothing then.
        KeyboardMapHooks.registerKeyMapping();
        LOGGER.info("QoL: Unified initialized successfully ({} features).", registry.features().size());
    }
}
