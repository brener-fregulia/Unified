package io.github.brener_fregulia.qolunified;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QolUnifiedClient implements ClientModInitializer {
    public static final String MOD_ID = "qol_unified";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("QoL: Unified initialized successfully.");
    }
}
