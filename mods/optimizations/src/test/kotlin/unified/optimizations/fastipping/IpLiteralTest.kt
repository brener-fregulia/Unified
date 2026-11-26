package unified.optimizations.fastipping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** fast-ip-ping-v1.md §6. */
class IpLiteralTest {
    @Test
    fun strictLiteralsAreAccepted() {
        for (host in listOf("192.168.1.20", "203.0.113.5", "127.0.0.1", "100.101.102.103", "0.0.0.0", "::1",
            "2001:db8::1", "fe80::1", "::ffff:192.0.2.1", "FD12:3456::1")) {
            assertNotNull(IpLiteral.parse(host), host)
        }
        assertEquals(4, IpLiteral.parse("10.0.0.1")!!.size)
        assertEquals(16, IpLiteral.parse("2001:db8::1")!!.size)
    }

    @Test
    fun everythingElseKeepsVanillaResolution() {
        for (host in listOf("127.1", "0x7f.0.0.1", "010.0.0.1", "999.1.1.1", "256.0.0.1", "1.2.3", "1..2.3", "",
            "1.2.3.4.5", "fe80::1%eth0", "localhost", "mc.example.com", "xn--bcher-kva.example", "[::1]",
            ":::", "1.2.3.4 ", "١٢٣.1.1.1")) {
            assertNull(IpLiteral.parse(host), host)
        }
        assertNull(IpLiteral.parse(null))
    }

    @Test
    fun onlyNonPublicAddressesTakeTheFastPath() {
        for (host in listOf("127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.10", "169.254.1.1",
            "100.64.0.1", "100.127.255.254", "::1", "fd00::1", "fc00::1", "fe80::1", "::ffff:192.168.1.1")) {
            assertTrue(IpLiteral.isNonPublic(IpLiteral.parse(host)!!), host)
        }
        for (host in listOf("8.8.8.8", "203.0.113.5", "172.32.0.1", "172.15.0.1", "100.128.0.1", "100.63.0.1",
            "192.169.0.1", "2001:db8::1", "2606:4700::1111", "::ffff:8.8.8.8")) {
            assertFalse(IpLiteral.isNonPublic(IpLiteral.parse(host)!!), host)
        }
    }
}
