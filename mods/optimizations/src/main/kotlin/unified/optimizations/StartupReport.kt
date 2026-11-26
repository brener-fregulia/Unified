package unified.optimizations

import java.nio.file.Path

/**
 * The one-line startup report. Kotlin (monorepo language policy) calling the Java [OptimizationsPaths], and called
 * from the Java entrypoint.
 */
object StartupReport {
    @JvmStatic
    @JvmOverloads // Java sees message(Path) as well as message(Path, int)
    fun message(gameConfigDirectory: Path, optimizations: Int = 0): String {
        val state = if (optimizations == 0) "no optimizations enabled yet" else "$optimizations optimizations"
        return "Unified: Optimizations initialized ($state). Configuration directory: " +
            OptimizationsPaths.configDirectory(gameConfigDirectory)
    }
}
