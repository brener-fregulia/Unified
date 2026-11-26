package unified.spike.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records what Fabric Loader actually loaded for one scenario, writes it to spike-result.txt in the run directory and
 * logs it, then closes the game. The library API is reached by name so that the runner works with any library
 * version, including the breaking 2.0.0.
 */
public final class SpikeRunner implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("spike_runner");

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
            List<String> lines = new ArrayList<>();
            String scenario = System.getProperty("spike.scenario", "?");
            lines.add("scenario=" + scenario);
            List<ModContainer> libs = FabricLoader.getInstance().getAllMods().stream()
                    .filter(m -> m.getMetadata().getId().equals("unified_settings")).toList();
            lines.add("unified_settings instances=" + libs.size());
            for (ModContainer lib : libs) {
                lines.add("unified_settings version=" + lib.getMetadata().getVersion().getFriendlyString()
                        + " nestedIn=" + lib.getContainingMod().map(m -> m.getMetadata().getId()).orElse("-"));
            }
            lines.add("mods=" + FabricLoader.getInstance().getAllMods().stream()
                    .map(m -> m.getMetadata().getId()).filter(id -> id.startsWith("spike_")
                            || id.startsWith("unified") || id.equals("qol_unified"))
                    .sorted().collect(Collectors.joining(",")));
            try {
                Class<?> api = Class.forName("unified.settings.spike.UnifiedSettings");
                lines.add("api.version=" + api.getMethod("version").invoke(null));
                lines.add("api.initCount=" + api.getMethod("initCount").invoke(null));
                lines.add("api.providers=" + api.getMethod("describeProviders").invoke(null));
                lines.add("api.classResources=" + Collections.list(api.getClassLoader()
                        .getResources("unified/settings/spike/UnifiedSettings.class")).size());
            } catch (ReflectiveOperationException | IOException | RuntimeException e) {
                lines.add("api.error=" + e);
            }
            long keys = java.util.Arrays.stream(minecraft.options.keyMappings)
                    .map(KeyMapping::getName).filter("key.unified_settings.spike"::equals).count();
            lines.add("keyMappings=" + keys);
            lines.forEach(l -> LOGGER.info("[spike-result] {}", l));
            try {
                Files.write(Path.of("spike-result.txt"), lines);
            } catch (IOException e) {
                LOGGER.error("could not write the result", e);
            }
            minecraft.stop();
        });
    }
}
