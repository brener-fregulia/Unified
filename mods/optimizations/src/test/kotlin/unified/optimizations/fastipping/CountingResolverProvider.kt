package unified.optimizations.fastipping

import java.net.InetAddress
import java.net.spi.InetAddressResolver
import java.net.spi.InetAddressResolverProvider
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

/**
 * Test runtime only (META-INF/services in src/test): counts the JVM's forward and reverse DNS lookups and answers
 * without any network: reverse lookups return a fixed name, forward lookups know only the names in [hosts].
 */
class CountingResolverProvider : InetAddressResolverProvider() {
    override fun get(configuration: Configuration): InetAddressResolver = object : InetAddressResolver {
        override fun lookupByName(host: String, policy: InetAddressResolver.LookupPolicy): Stream<InetAddress> {
            forward.incrementAndGet()
            // The JDK confirms a PTR answer with a forward lookup of that name.
            val bytes = (if (host == PTR_NAME) lastReversed else hosts[host]) ?: throw java.net.UnknownHostException(host)
            return Stream.of(InetAddress.getByAddress(host, bytes))
        }

        override fun lookupByAddress(addr: ByteArray): String {
            reverse.incrementAndGet()
            lastReversed = addr.clone()
            return PTR_NAME
        }
    }

    override fun name(): String = "unified-optimizations-test-counting"

    companion object {
        const val PTR_NAME: String = "ptr.blocked.example"
        val forward = AtomicInteger()
        val reverse = AtomicInteger()
        @Volatile
        var lastReversed: ByteArray? = null
        val hosts = mutableMapOf("localhost" to byteArrayOf(127, 0, 0, 1), "mc.example.com" to byteArrayOf(203.toByte(), 0, 113, 5))

        fun reset() {
            forward.set(0)
            reverse.set(0)
        }
    }
}
