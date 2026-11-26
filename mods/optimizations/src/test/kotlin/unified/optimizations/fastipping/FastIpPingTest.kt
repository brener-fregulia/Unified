package unified.optimizations.fastipping

import java.util.Optional
import net.minecraft.client.multiplayer.resolver.AddressCheck
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The lookups are counted by [CountingResolverProvider], installed for the test JVM only, so the absence of the
 * reverse lookup is proven by calls, not timings (fast-ip-ping-v1.md §10).
 */
class FastIpPingTest {
    /** Records what the blocklist predicates are asked, like vanilla's AddressCheck: host name and IP, then host. */
    private class RecordingCheck(private val blocked: Set<String> = emptySet()) : AddressCheck {
        val asked = mutableListOf<String>()

        override fun isAllowed(address: ResolvedServerAddress): Boolean {
            val name = address.hostName
            val ip = address.hostIp
            asked += "resolved:$name/$ip"
            return name !in blocked && ip !in blocked
        }

        override fun isAllowed(address: ServerAddress): Boolean {
            asked += "typed:${address.host}"
            return address.host !in blocked
        }
    }

    /** Vanilla's ServerNameResolver steps for a host that resolves directly: SYSTEM resolution, then the checks. */
    private fun vanilla(address: ServerAddress, check: AddressCheck): Optional<ResolvedServerAddress> {
        val resolved = ServerAddressResolver.SYSTEM.resolve(address)
        if (resolved.isPresent) {
            return if (check.isAllowed(resolved.get()) && check.isAllowed(address)) resolved else Optional.empty()
        }
        return Optional.empty() // the SRV step runs only here, in vanilla; the fast path never reaches it
    }

    @BeforeEach
    fun reset() {
        // Load Minecraft's classes and logging first: their one-time setup resolves the local host name.
        ServerAddress("warm.up", 1)
        ResolvedServerAddress.from(java.net.InetSocketAddress(java.net.InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4)), 1))
        ServerAddressResolver.LOGGER.isDebugEnabled
        CountingResolverProvider.reset()
    }

    @Test
    fun vanillaReverseResolvesALiteralForTheBlocklist() {
        val check = RecordingCheck()
        vanilla(ServerAddress("192.168.1.20", 25565), check)
        assertEquals(1, CountingResolverProvider.reverse.get(), "the problem: one PTR query per ping")
        assertTrue(check.asked.first().startsWith("resolved:") && check.asked.first().endsWith("/192.168.1.20"))
    }

    @Test
    fun nonPublicLiteralsSkipEveryLookupAndKeepPortAndChecks() {
        for ((host, port) in listOf("192.168.1.20" to 25565, "127.0.0.1" to 25570, "100.101.102.103" to 1,
            "10.0.0.7" to 65535, "::1" to 25565, "fd7a:115c:a1e0::1" to 25566)) {
            reset()
            val check = RecordingCheck()
            val result = FastIpPing.resolve(ServerAddress(host, port), check)!!
            assertEquals(0, CountingResolverProvider.reverse.get(), "$host: no reverse lookup")
            assertEquals(0, CountingResolverProvider.forward.get(), "$host: no forward lookup")
            val resolved = result.get()
            assertEquals(port, resolved.port, "$host: port preserved")
            assertEquals(port, resolved.asInetSocketAddress().port)
            assertEquals(java.net.InetAddress.ofLiteral(host), resolved.asInetSocketAddress().address, host)
            assertEquals(listOf("resolved:$host/${resolved.hostIp}", "typed:$host"), check.asked,
                "$host: vanilla's two checks in vanilla's order, with the typed text as the host name")
        }
    }

    @Test
    fun blockedAddressesAreStillRefused() {
        assertEquals(Optional.empty<ResolvedServerAddress>(),
            FastIpPing.resolve(ServerAddress("192.168.1.20", 25565), RecordingCheck(setOf("192.168.1.20"))))
        assertEquals(0, CountingResolverProvider.reverse.get())
    }

    @Test
    fun publicLiteralsHostnamesAndInvalidTextKeepVanilla() {
        for (host in listOf("8.8.8.8", "2001:db8::1", "localhost", "mc.example.com", "999.1.1.1", "1..2.3", "127.1",
            "fe80::1%eth0", "")) {
            assertNull(FastIpPing.resolve(ServerAddress(host, 25565), RecordingCheck()), host)
            assertEquals(0, CountingResolverProvider.reverse.get() + CountingResolverProvider.forward.get(), "after $host")
        }
        assertEquals(0, CountingResolverProvider.reverse.get() + CountingResolverProvider.forward.get(),
            "deciding to fall back performs no lookup")
        // A hostname then resolves as in vanilla: one forward lookup, the name kept, no reverse lookup.
        val check = RecordingCheck()
        val result = vanilla(ServerAddress("localhost", 25565), check)
        assertTrue(result.isPresent)
        assertEquals(1, CountingResolverProvider.forward.get())
        assertEquals(0, CountingResolverProvider.reverse.get())
        // An unknown name fails resolution, where vanilla would try its SRV record next.
        assertFalse(vanilla(ServerAddress("no-such-host.invalid", 25565), RecordingCheck()).isPresent)
    }
}
