package io.github.brener_fregulia.unified.optimizations;

import java.nio.file.Path;

/**
 * Where this mod's files belong (docs/optimizations/README.md, configuration). Each Unified mod owns its own directory
 * under the game's config directory, so neither ever reads or writes another's files. Nothing is written until a
 * feature with settings is approved.
 */
public final class OptimizationsPaths {
    private OptimizationsPaths() {
    }

    /** {@code <config>/unified_optimizations}, separate from Unified: QoL's {@code <config>/qol_unified}. */
    public static Path configDirectory(Path gameConfigDirectory) {
        return gameConfigDirectory.resolve(UnifiedOptimizationsClient.MOD_ID);
    }

    /** The future configuration file. */
    public static Path configFile(Path gameConfigDirectory) {
        return configDirectory(gameConfigDirectory).resolve("config.json");
    }
}
