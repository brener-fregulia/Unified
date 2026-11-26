package unified.optimizations.dnsprobe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Development game tests only (on the boot class path of the fastipping run): wraps the JVM's built-in resolver and counts lookups, so the tests prove which pings
 * perform a reverse (PTR) lookup. The JDK loads it from the boot class path while the game code runs in
 * Fabric's class loader, so the counters live in the JDK-shared system properties table.
 */
public final class CountingDns extends InetAddressResolverProvider {
    public static final String REVERSE = "unified_optimizations.test.dns.reverse";
    public static final String FORWARD = "unified_optimizations.test.dns.forward";

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                counter(FORWARD).incrementAndGet();
                return builtin.lookupByName(host, policy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                counter(REVERSE).incrementAndGet();
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "unified-optimizations-gametest-counting";
    }

    public static AtomicInteger counter(String name) {
        return (AtomicInteger) System.getProperties().computeIfAbsent(name, k -> new AtomicInteger());
    }

    public static void reset() {
        counter(REVERSE).set(0);
        counter(FORWARD).set(0);
    }
}
