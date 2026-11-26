package unified.optimizations.core

/**
 * One optimization: its identity, where it appears in Unified Settings and its default. Only implemented features are
 * listed in [OptimizationFeatures.ALL].
 *
 * @param conflicts Fabric mod ids that do the same job; while one is installed the feature stays inactive
 */
data class OptimizationFeature(
    val id: String,
    val category: Category,
    val defaultEnabled: Boolean,
    val conflicts: List<String> = emptyList(),
) {
    val nameKey: String get() = "unified_optimizations.feature.$id.name"
    val descriptionKey: String get() = "unified_optimizations.feature.$id.description"

    /** Settings categories, in display order. */
    enum class Category(val id: String) {
        NETWORK("network"),
        PERFORMANCE("performance");

        val nameKey: String get() = "unified_optimizations.category.$id"
    }
}

/** The implemented optimizations (docs/optimizations/README.md, catalog). */
object OptimizationFeatures {
    /** Fast IP Ping: no reverse DNS for server-list pings of non-public literal IPs (fast-ip-ping-v1.md). */
    @JvmField
    val FAST_IP_PING = OptimizationFeature("fast_ip_ping", OptimizationFeature.Category.NETWORK, true,
        listOf("fastipping", "fastserverpings"))

    /** FastQuit: return to the menu while the world finishes saving (fastquit-v1.md). Conservative default: off. */
    @JvmField
    val FAST_QUIT = OptimizationFeature("fast_quit", OptimizationFeature.Category.PERFORMANCE, false,
        listOf("fastquit"))

    @JvmField
    val ALL: List<OptimizationFeature> = listOf(FAST_IP_PING, FAST_QUIT)

    @JvmStatic
    fun byId(id: String): OptimizationFeature? = ALL.firstOrNull { it.id == id }
}
