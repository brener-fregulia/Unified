package unified.qol.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Friendly mod names for registry namespaces (sound-control-v1.md Q-SC10, target-info-v1.md Q-TI8): the display name
 * from Fabric Loader metadata, or the namespace itself when no mod with that id is loaded or its name is blank.
 */
public final class ModNames {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private ModNames() {
    }

    public static String displayName(String namespace) {
        return CACHE.computeIfAbsent(namespace, ns -> FabricLoader.getInstance().getModContainer(ns)
                .map(c -> c.getMetadata().getName()).filter(n -> !n.isBlank()).orElse(ns));
    }
}
