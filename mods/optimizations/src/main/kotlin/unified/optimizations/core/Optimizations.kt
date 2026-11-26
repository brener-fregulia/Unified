package unified.optimizations.core

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * The runtime state of the optimizations: whether each is enabled (the player's choice, or its default) and active
 * (enabled and not displaced by a conflicting mod). Reads are lock-free, so Mixins on any thread (e.g. the server
 * pinger threads) can check a feature cheaply. Changes apply live: a feature checks [isActive] each time it acts.
 */
object Optimizations {
    private val logger = LoggerFactory.getLogger("unified_optimizations")
    private val enabled = ConcurrentHashMap<String, Boolean>()
    private val conflicts = ConcurrentHashMap<String, List<String>>()
    private var config: OptimizationsConfig? = null

    /** Loads the configuration and detects conflicting mods; called once from the client entrypoint. */
    @JvmStatic
    fun start(configFile: Path, isModLoaded: (String) -> Boolean): OptimizationsConfig {
        val loaded = OptimizationsConfig(configFile)
        val status = loaded.load()
        if (status == OptimizationsConfig.LoadStatus.INVALID || status == OptimizationsConfig.LoadStatus.NEWER) {
            logger.warn("Configuration {}: {}; using defaults", configFile, status)
        }
        config = loaded
        for (feature in OptimizationFeatures.ALL) {
            val found = feature.conflicts.filter(isModLoaded)
            conflicts[feature.id] = found
            if (found.isNotEmpty()) {
                logger.warn("[{}] inactive: {} installed", feature.id, found.joinToString())
            }
            enabled[feature.id] = loaded.choice(feature.id) ?: feature.defaultEnabled
        }
        return loaded
    }

    @JvmStatic
    fun isEnabled(featureId: String): Boolean = enabled[featureId] ?: false

    /** Enabled and not displaced by a conflicting mod. */
    @JvmStatic
    fun isActive(featureId: String): Boolean = isEnabled(featureId) && conflicts[featureId].isNullOrEmpty()

    /** How many features are active now. */
    @JvmStatic
    fun activeCount(): Int = OptimizationFeatures.ALL.count { isActive(it.id) }

    @JvmStatic
    fun conflictsOf(featureId: String): List<String> = conflicts[featureId] ?: emptyList()

    /**
     * Saves the player's choice, then applies it. If the save fails nothing changes, and the error is returned for
     * the settings panel to show.
     */
    @JvmStatic
    fun setEnabled(featureId: String, on: Boolean): IOException? {
        val current = config ?: return IOException("not started")
        return try {
            current.save(featureId, on)
            enabled[featureId] = on
            logger.info("[{}] {}", featureId, if (on) "enabled" else "disabled")
            null
        } catch (e: IOException) {
            logger.error("[{}] could not save the configuration", featureId, e)
            e
        } catch (e: RuntimeException) {
            logger.error("[{}] could not save the configuration", featureId, e)
            IOException(e)
        }
    }

    /** For unit tests. */
    @JvmStatic
    fun startForTest(configFile: Path, installed: Set<String> = emptySet()) = start(configFile) { it in installed }
}
