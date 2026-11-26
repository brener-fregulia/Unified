package unified.optimizations.fastipping

import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Optional
import net.minecraft.client.multiplayer.resolver.AddressCheck
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress
import net.minecraft.client.multiplayer.resolver.ServerAddress

/**
 * Server-list ping resolution for non-public literal IPs without the reverse DNS lookup (fast-ip-ping-v1.md §5).
 *
 * Vanilla resolves a literal with `InetAddress.getByName` (no lookup, but no hostname kept), and then its blocklist
 * check asks the address for a hostname, which is a reverse (PTR) query. Here the address is built from the literal's
 * bytes with the typed text as its hostname, so the same check runs on the typed text and the IP, in vanilla's order,
 * without a query. For any other host (public IPs, hostnames, invalid text) the caller runs vanilla.
 */
object FastIpPing {
    /** The fast result, or null when vanilla must resolve [address]. */
    @JvmStatic
    fun resolve(address: ServerAddress, check: AddressCheck): Optional<ResolvedServerAddress>? {
        val host = address.host
        val bytes = IpLiteral.parse(host) ?: return null
        if (!IpLiteral.isNonPublic(bytes)) return null
        val inet = try {
            InetAddress.getByAddress(host, bytes)
        } catch (e: Exception) {
            return null
        }
        val resolved = ResolvedServerAddress.from(InetSocketAddress(inet, address.port))
        // Vanilla's order: the resolved address (host name and IP), then the typed address.
        return if (check.isAllowed(resolved) && check.isAllowed(address)) Optional.of(resolved) else Optional.empty()
    }
}
