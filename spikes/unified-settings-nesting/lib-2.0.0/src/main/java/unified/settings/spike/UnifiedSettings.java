package unified.settings.spike;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Library 2.0.0 of the spike: the single owner of the shared key mapping, and the provider registry. */
public final class UnifiedSettings implements ClientModInitializer {
    public static final String VERSION = "2.0.0";
    /** Unbound on purpose: the production U binding is not registered by the spike. */
    public static final String KEY = "key.unified_settings.spike";
    private static final Logger LOGGER = LoggerFactory.getLogger("unified_settings");
    private static int initCount;

    @Override
    public void onInitializeClient() {
        initCount++;
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("unified_settings",
                "spike"));
        KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY, InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(), category));
        LOGGER.info("[spike] unified_settings {} initialized (call {})", VERSION, initCount);
    }

    public static String version() {
        return VERSION;
    }

    public static int initCount() {
        return initCount;
    }

    /** Asks every provider entrypoint to describe itself; a failing provider is isolated and reported. */
    public static List<String> describeProviders() {
        List<String> out = new ArrayList<>();
        for (var container : FabricLoader.getInstance().getEntrypointContainers("unified-settings",
                SettingsProvider.class)) {
            String id = container.getProvider().getMetadata().getId();
            try {
                SettingsProvider p = container.getEntrypoint();
                out.add(id + ": " + p.title() + " order " + p.order());
            } catch (Throwable t) {
                out.add(id + ": FAILED " + t);
            }
        }
        return out;
    }
}
