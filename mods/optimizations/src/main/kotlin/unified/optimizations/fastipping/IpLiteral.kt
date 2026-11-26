package unified.optimizations.fastipping

import java.net.InetAddress

/**
 * Strict IP literal classification (fast-ip-ping-v1.md §6). Pure: it never touches DNS. Anything it rejects keeps
 * vanilla's resolution, so only a wrong "yes" could change behavior; the accepted forms are therefore narrow.
 */
object IpLiteral {
    private val DOTTED_QUAD = Regex("(0|[1-9][0-9]{0,2})([.](0|[1-9][0-9]{0,2})){3}")

    /**
     * The address bytes of a strict literal: a dotted quad with parts 0–255 and no leading zeros, or an IPv6 literal
     * without a scope id. Null for hostnames, legacy or shortened IPv4 forms and invalid text.
     */
    @JvmStatic
    fun parse(host: String?): ByteArray? {
        if (host.isNullOrEmpty()) return null
        if (DOTTED_QUAD.matches(host)) {
            val parts = host.split('.').map(String::toInt)
            if (parts.any { it > 255 }) return null
            return ByteArray(4) { parts[it].toByte() }
        }
        if (':' in host && '%' !in host && host.all { it.isLetterOrDigit() || it == ':' || it == '.' }) {
            return try {
                InetAddress.ofLiteral(host).address // 16 bytes, or 4 for an IPv4-mapped literal
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        return null
    }

    /**
     * Whether [bytes] is an address that is not reachable on the public internet: loopback, private (RFC 1918),
     * link-local, carrier-grade NAT / Tailscale (100.64.0.0/10), IPv6 unique-local (fc00::/7) or link-local, and the
     * IPv4-mapped forms of those. Mojang's blocklist targets public servers, so these are where the reverse lookup is
     * both useless for the blocklist and most often slow (no PTR record).
     */
    @JvmStatic
    fun isNonPublic(bytes: ByteArray): Boolean {
        val b = bytes.map { it.toInt() and 0xFF }
        if (b.size == 4) return isNonPublic4(b)
        if (b.size != 16) return false
        val mapped = (0 until 10).all { b[it] == 0 } && b[10] == 0xFF && b[11] == 0xFF
        if (mapped) return isNonPublic4(b.subList(12, 16))
        val loopback = (0 until 15).all { b[it] == 0 } && b[15] == 1
        val uniqueLocal = b[0] and 0xFE == 0xFC
        val linkLocal = b[0] == 0xFE && b[1] and 0xC0 == 0x80
        return loopback || uniqueLocal || linkLocal
    }

    private fun isNonPublic4(b: List<Int>): Boolean =
        b[0] == 127 || b[0] == 10 ||
            (b[0] == 172 && b[1] in 16..31) ||
            (b[0] == 192 && b[1] == 168) ||
            (b[0] == 169 && b[1] == 254) ||
            (b[0] == 100 && b[1] in 64..127)
}
