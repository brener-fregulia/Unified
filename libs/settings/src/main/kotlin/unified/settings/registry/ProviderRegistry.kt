package unified.settings.registry

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import unified.settings.api.UnifiedSettingsProvider

/** One installed module: its provider, or why it could not be created. */
data class ModuleEntry(val modId: String, val provider: UnifiedSettingsProvider?, val failure: Throwable?) {
    val available: Boolean get() = provider != null
}

/**
 * The installed modules, discovered once from Fabric entrypoint metadata (no classpath scanning) and cached for the
 * session. A provider that fails to load or to answer is isolated: it is logged with its mod id and shown as failed;
 * the others work. Order: [UnifiedSettingsProvider.order], then mod id, so it never depends on load order.
 */
object ProviderRegistry {
    private val logger = LoggerFactory.getLogger("unified_settings")

    @Volatile
    private var cached: List<ModuleEntry>? = null

    /** The installed modules in display order; resolved on first use. */
    @JvmStatic
    fun modules(): List<ModuleEntry> = cached ?: synchronized(this) {
        cached ?: resolve(FabricLoader.getInstance()
            .getEntrypointContainers(UnifiedSettingsProvider.ENTRYPOINT, UnifiedSettingsProvider::class.java)
            .map { container -> Candidate(container.provider.metadata.id) { container.entrypoint } })
            .also { cached = it }
    }

    /** A provider to create: the declaring mod's id and how to obtain the instance. */
    class Candidate(val modId: String, val create: () -> UnifiedSettingsProvider)

    /** Creates, validates and orders providers; pure apart from logging, so it is unit-tested directly. */
    @JvmStatic
    fun resolve(candidates: List<Candidate>): List<ModuleEntry> {
        val entries = candidates.map { candidate ->
            try {
                val provider = candidate.create()
                // Read the identity once, so a provider that throws here is isolated now and not in the UI.
                provider.order
                provider.nameKey
                if (provider.moduleId != candidate.modId) {
                    logger.warn("Settings provider of {} reports module id {}", candidate.modId, provider.moduleId)
                }
                ModuleEntry(candidate.modId, provider, null)
            } catch (t: Throwable) {
                logger.error("Settings provider of {} failed to load", candidate.modId, t)
                ModuleEntry(candidate.modId, null, t)
            }
        }
        return entries
            .distinctBy { it.modId }
            .sortedWith(compareBy<ModuleEntry>({ it.provider?.order ?: Int.MAX_VALUE }, { it.modId }))
    }

    /** For tests: forget the cached modules. */
    @JvmStatic
    fun reset() {
        cached = null
    }
}
