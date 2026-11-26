# Fast IP Ping V1 (M2.2)

> **Implemented 2026-11-26 (M2.2).** See §15 for the implemented scope, the
> blocklist finding that narrowed it, and the decisions still open. Sections
> 1–14 are the original proposal, kept for history.

Status labels follow [AGENTS.md](../../../AGENTS.md#decision-status-labels).
The Product Owner approved the direction (feature, milestone, category,
client-only). Every implementation decision below is **[Proposed]** or
**[Open]** until approved; §13 lists the questions.

**Ownership (2026-11-26):** with the monorepo migration this specification
moved from Unified: QoL to [Unified: Optimizations](../README.md). Its content
and open decisions are unchanged. References below to "Core policy", the
settings workspace and auto-apply were written for Unified: QoL's Core, which
Optimizations does not have. Q-FIP7 records that gap.

Avoid the reverse DNS lookup that Minecraft performs when the multiplayer
server list pings a server entered as a literal IP address. Inspired by the
behavior of Fast IP Ping (Fallen_Breath). No third-party code or assets are
used.

## 1. Identity, goals and non-goals [Proposed]

| Item | Proposal |
| --- | --- |
| Feature id | `fast_ip_ping` |
| Milestone | M1.10 |
| Category | Performance & Network (`PERFORMANCE_NETWORK`, already in `FeatureCategory`) |
| Default | Enabled |
| Apply mode | LIVE |
| Side | Client only: no packets, no protocol change, no server component |

### 1.1 Goals

- A server-list ping to a literal IPv4 or IPv6 address never triggers a
  reverse (PTR) DNS lookup.
- Hostnames, SRV records, ports, timeouts, error messages and the server
  blocklist check behave exactly as in vanilla.
- The feature affects only pings started while it is on.

### 1.2 Non-goals

- Global DNS changes, a custom resolver, caching, new threads or sockets.
- Faster pinging in general (parallelism, caching): that is Fast Server
  Pings' territory and out of scope.
- Joining servers, Direct Connect, LAN and Realms (§7).

## 2. Research

### 2.1 Mods

| Mod | Version (Modrinth, Fabric) | Fabric id (from the JAR's `fabric.mod.json`) | License | What it does |
| --- | --- | --- | --- | --- |
| [Fast IP Ping](https://modrinth.com/mod/fast-ip-ping) ([source](https://github.com/Fallen-Breath/fast-ip-ping)) | `v1.0.12-mc26.3`, 2026-09-17; builds for 1.21.9 through 26.3 | `fastipping` | LGPL-3.0 | "Yeet the laggy reversed DNS lookup for literal IP server addresses." Three Mixins: `AddressResolverMixin`, `ConnectScreenThreadMixin`, `MultiplayerServerListPingerMixin`. References Guava `InetAddresses.isInetAddress` and `InetAddress.getByAddress(String, byte[])`. So it covers both pinging and joining. |
| [Fast Server Pings](https://modrinth.com/mod/fastserverpings) | `1.1.5+26.3` (also `+26.4`), 2026-11-14 | `fastserverpings` | MIT | "Rewritten server ping pipeline using optimized Netty I/O … and SWR caching." One Mixin, `ServerPingerMixin` (priority 2000). Its only address API reference is `InetSocketAddress.createUnresolved`; it references neither vanilla's `ServerNameResolver` nor `AddressCheck`. |

Findings:

1. **Fast IP Ping** fixes exactly the reverse lookup, using Mixins (no public
   API exists for this). It also covers the join path.
2. **Fast Server Pings** replaces the ping pipeline. Judging only by its class
   references, its pings bypass vanilla's resolver and blocklist check
   entirely, so the reverse lookup is avoided as a side effect. That was not
   verified at runtime. It is a broader, riskier change than this feature.
3. **Still relevant for 26.3:** yes (§4).

Only metadata, Mixin names and referenced JDK/Minecraft symbols were
inspected. No method bodies were read or copied.

## 3. Minecraft 26.3 call path (verified with `javap`)

```
JoinMultiplayerScreen
 └ ServerSelectionList.OnlineServerEntry
    └ THREAD_POOL.submit(...)            "Server Pinger #%d", ScheduledThreadPoolExecutor(5)
       └ ServerStatusPinger.pingServer(ServerData, Runnable, Runnable, EventLoopGroupHolder)
          ├ ServerAddress.parseString(serverData.ip)   Guava HostAndPort; IDN.toASCII; brackets removed
          ├ ServerNameResolver.DEFAULT.resolveAddress(ServerAddress)
          │   1. resolver.resolve(address)             SYSTEM resolver:
          │        InetAddress.getByName(host)          literal → no lookup, but no hostname kept
          │        new InetSocketAddress(addr, port) → ResolvedServerAddress.from(...)
          │   2. if resolved:
          │        addressCheck.isAllowed(resolved)    AddressCheck$1:
          │          resolved.getHostName()             → InetAddress.getHostName()   ← REVERSE LOOKUP
          │          resolved.getHostIp()
          │          blocklist predicates (Patchy MojangBlockListSupplier via ServiceLoader)
          │        addressCheck.isAllowed(address)
          │        → result, or empty if blocked
          │   3. only if step 1 failed: SRV redirect (JNDI "dns:", `_minecraft._tcp.<host>`), resolve, check
          ├ Connection.connectToServer(InetSocketAddress, ...)      no hostname use
          └ initiateServerboundStatusConnection(address.getHost(), port, ...)  the typed host string
```

- **The only reverse lookup in the ping path** is
  `ResolvedServerAddress.getHostName()` →
  `InetSocketAddress.getAddress().getHostName()`, called by the blocklist
  check. `ServerStatusPinger`, its listeners, `LegacyServerPinger` and
  `Connection` make no hostname or DNS calls (`javap`: none found).
- **The JDK side:** an `InetAddress` created by `getByName` from a literal
  carries no hostname. Its `getHostName()` asks the name service for one: a
  PTR query, which the JDK verifies with a forward lookup. A hostname entry
  keeps its name and is not reverse-resolved.
- **SRV is never consulted for literals,** because step 1 always succeeds for
  them.
- **Join path:** `ConnectScreen$1` (the connect thread) also uses
  `ServerNameResolver.DEFAULT.resolveAddress`, and therefore the same
  lookup. It calls `InetSocketAddress.getHostName()` again in its error
  handling.

## 4. The problem [Proposed]

- **When it happens:** every ping of a literal-IP entry performs a PTR lookup
  on a "Server Pinger" thread.
- **Why it hurts:** when the address has no PTR record or the DNS server is
  slow, the lookup waits for the resolver timeout before the status request
  is even sent. During that time one of only five pinger threads is
  occupied, which delays the other entries.
- **Who is affected:** private, LAN, VPN and Tailscale (`100.64.0.0/10`)
  addresses, which typically have no PTR record.
- **Unaffected:** the render thread, and hostname entries.

The size of the delay depends on the operating system's resolver and the
network. **No timing is claimed** (§10).

**Blocklist detail.** For a literal, vanilla's PTR name is matched against
Mojang's blocklist as a hostname, next to the IP. Skipping the lookup means a
server list *ping* no longer matches a blocked hostname found only through
PTR. The IP check is unchanged. Joining is unchanged in V1, so blocklist
enforcement on join is untouched (Q-FIP3).

## 5. Proposed optimization [Proposed]

- **One injection Mixin:** a MixinExtras `@WrapOperation` in
  `ServerStatusPinger.pingServer` around its call to
  `ServerNameResolver.resolveAddress(ServerAddress)`.
- **One accessor:** on `ServerNameResolver`, reading its private
  `addressCheck`.
- **Behavior while active, when the host is a strict IP literal (§6):**
  1. Build the address from the literal with `InetAddress.ofLiteral` (JDK
     22+, parses without any lookup).
  2. Rebuild it with `InetAddress.getByAddress(host, bytes)`, so its hostname
     is the literal text the player typed and `getHostName()` needs no
     lookup.
  3. Wrap it in `ResolvedServerAddress.from(new InetSocketAddress(..., port))`.
  4. Apply `addressCheck.isAllowed(resolved)`, then
     `addressCheck.isAllowed(address)`, in vanilla's order. A blocked result
     returns `Optional.empty()`, exactly as vanilla.
- **Everything else** (feature off, hostnames, anything not a strict literal)
  calls the original method.
- **Work:** runs on the existing pinger thread. No new thread, executor,
  cache, reflection or socket.
- **Disable:** checked once per ping when resolution starts. Pings already in
  flight finish unchanged (Q-FIP4).

## 6. IPv4 / IPv6 handling [Proposed]

A pure `IpLiteral` classifier (no Minecraft types, no DNS) decides whether
the fast path applies. Anything it does not accept falls back to vanilla, so
a wrong "no" costs only the old behavior. Only a wrong "yes" could change
behavior, which is why the accepted forms are narrow.

| Input (`ServerAddress.getHost()`) | Fast path | Note |
| --- | --- | --- |
| `192.168.1.20`, `203.0.113.5` | Yes | Strict dotted quad: four decimal parts 0–255, no leading zeros except `0` |
| `192.168.1.20:25570` | Yes | The port comes from `ServerAddress`, untouched |
| `127.0.0.1` | Yes | |
| `100.101.102.103` (Tailscale) | Yes | |
| `127.1`, `0x7f.0.0.1`, `010.0.0.1` | No | Legacy or octal forms that Java accepts but people rarely mean; vanilla |
| `::1`, `2001:db8::1`, `fe80::1` | Yes | Host contains `:`, so it can never be a hostname; parsed by `Inet6Address.ofLiteral` |
| `[2001:db8::1]:25565` | Yes | `HostAndPort` already removed the brackets |
| `::ffff:192.0.2.1` | Yes | Parsed by the JDK |
| `fe80::1%eth0` (scope id) | No | Scope handling stays with vanilla |
| `localhost`, `mc.example.com`, `xn--…` (IDN) | No | Hostnames: vanilla resolution and SRV |
| `999.1.1.1`, `1.2.3`, `1..2.3`, empty | No | Invalid or not a strict literal: vanilla error handling |

A literal that parses also passes the JDK's own `ofLiteral`. If that throws,
the fast path is abandoned and vanilla runs.

## 7. Scope [Proposed]

| Path | V1 | Reason |
| --- | --- | --- |
| Multiplayer server-list ping | **Yes** | The reported problem; display-only result |
| Direct Connect | No (Q-FIP2) | Joins go through `ConnectScreen`, the authenticated join and blocklist enforcement |
| Joining a listed server | No (Q-FIP2) | Same path as Direct Connect |
| LAN discovery | No | Multicast discovery, not `ServerStatusPinger` |
| Realms | No | Own pipeline; Realms addresses are hostnames |

## 8. Compatibility [Proposed]

| Mod | Fabric id (verified in JAR) | Overlap | Proposal |
| --- | --- | --- | --- |
| Fast IP Ping | `fastipping` | Same fix on the same path | Known conflict (Core policy: `fast_ip_ping` off by default while present) |
| Fast Server Pings | `fastserverpings` | Replaces the pinger. Its high-priority Mixin may make ours a no-op or conflict at the same call. | Known conflict (Q-FIP5) |
| Sodium, Iris, other performance mods | — | None | Not conflicts |
| Tailscale / private networks | — | Benefit most | QA case |

Java DNS behavior is untouched: no `InetAddressResolverProvider`, no system
properties and no security settings are changed.

## 9. Configuration [Proposed]

- One master switch, with no extra settings.
- A short description in en_us and pt_br, e.g. "Pings servers listed by IP
  address without a slow reverse DNS lookup."
- No settings screen, no hotkey.

## 10. Performance and verification [Proposed]

- **Cost:** per ping, one string classification and one byte-array copy,
  replacing a network lookup. Nothing runs when the server list is closed.
- **How the lookup is proven gone:** not by elapsed time. Unit tests install
  a counting `java.net.spi.InetAddressResolverProvider` through
  `META-INF/services` in the **test runtime only**, and assert:
  - the vanilla path for a literal calls `lookupByAddress` (proving the
    problem);
  - the fast path calls neither `lookupByAddress` nor `lookupByName`;
  - a hostname path still calls `lookupByName`.
- **Timing:** no numbers are claimed without a benchmark.

## 11. Acceptance criteria [Proposed]

1. **FIP-AC-1:** pinging `127.0.0.1:<port>` performs zero reverse lookups
   (counting resolver) and shows the server's MOTD and player count.
2. **FIP-AC-2:** `[::1]:<port>` likewise, where the test machine supports IPv6
   loopback; skipped with a logged reason otherwise.
3. **FIP-AC-3:** a hostname (`localhost:<port>`) uses vanilla resolution: one
   forward lookup, result unchanged.
4. **FIP-AC-4:** SRV: for a hostname without an A record, vanilla's SRV step
   still runs (unit test with a stub redirect handler; no real DNS).
5. **FIP-AC-5:** custom ports are preserved; the connection goes to exactly
   the typed IP and port.
6. **FIP-AC-6:** invalid addresses (`999.1.1.1`, `1..2.3`) behave exactly as
   vanilla: same error text.
7. **FIP-AC-7:** an unreachable literal (`127.0.0.1` on a closed port) fails
   with vanilla's message and timing rules.
8. **FIP-AC-8:** a blocklisted IP is still refused by the ping (stub address
   check); the check order equals vanilla's.
9. **FIP-AC-9:** with the feature OFF, the counting resolver sees vanilla's
   reverse lookup again; toggling affects only later pings.
10. **FIP-AC-10:** no change to global DNS: no resolver provider or system
    property set in production code.
11. **FIP-AC-11:** strings in en_us and pt_br.
12. **FIP-AC-12:** with `fastipping` or `fastserverpings` present, the feature
    starts off unless the player enables it.
13. **FIP-AC-13:** no packets registered, no protocol change.
14. **FIP-AC-14:** existing tests and the settings workspace are unaffected.

## 12. QA [Proposed]

### 12.1 Automated

- **Unit tests:**
  - `IpLiteral` over the §6 table;
  - the fast resolution helper with the counting resolver provider (test
    runtime only), stub address checks and the SRV fallback;
  - settings, conflicts, translations.
- **Focused game test** `fastipping`:
  - Opens the disposable singleplayer world to LAN, which gives a real
    status endpoint on localhost.
  - Adds server entries for `127.0.0.1:<port>`, `localhost:<port>` and a
    closed port in a disposable `servers.dat` inside the run directory.
  - Pings them through the real `ServerStatusPinger` and checks the MOTD,
    errors and the counting resolver.
- **No router or real DNS changes,** and no access to the Product Owner's
  Paper server.

### 12.2 Manual (Windows 11 and CachyOS, Prism, production JAR)

1. Server list with the Paper server by **hostname** and the same server by
   **literal IP**: both show status. Then refresh repeatedly.
2. A Tailscale or LAN IP entry, if available.
3. An IPv6 literal entry, if the network has IPv6.
4. Turn the feature off and on and refresh.
5. Join the server by IP and by hostname: unchanged.
6. Português texts.

## 13. Open decisions for Product Owner approval

- **Q-FIP1 [Proposed]:** proceed. The problem is verified in 26.3 (§3, §4).
- **Q-FIP2 [Open]:** V1 limited to server-list pings (recommended). Joining
  and Direct Connect also reverse-resolve literals, but they carry the
  authenticated join and blocklist enforcement.
- **Q-FIP3 [Open]:** accept that, for literal IPs, the *ping* blocklist check
  uses the IP and the typed text but not the PTR name (joins unchanged), or
  keep the reverse lookup whenever a blocklist provider is present (which
  would make the feature ineffective in normal clients).
- **Q-FIP4 [Proposed]:** pings already running when the switch changes are
  left alone.
- **Q-FIP5 [Proposed]:** soft conflicts with `fastipping` and
  `fastserverpings`.
- **Q-FIP6 [Open]:** relationship to the catalog's existing `server_ping` (M3)
  entry. `fast_ip_ping` could become its first part, or a separate feature
  with `server_ping` kept for broader ping work.

- **Q-FIP7 [Open]:** Unified: Optimizations has no Core, settings UI or
  conflict policy yet. Decide how its switch, persistence and soft conflicts
  are provided: a minimal module-local equivalent, or deferring the switch.
  The mods must stay independent, with no shared runtime library (see the
  [monorepo architecture](../../architecture/monorepo.md)).

## 14. Deferred or excluded

- **Deferred:** the join and Direct Connect paths (Q-FIP2).
- **Excluded:** ping caching or parallelism, LAN and Realms, any DNS cache,
  and global resolver changes.

## 15. M2.2 implementation [Implemented 2026-11-26]

### 15.1 Blocklist finding (resolves the research for Q-FIP3)

Verified in 26.3 and Patchy 2.2.10 (`javap`):

- `AddressCheck.createFromService()` loads every `BlockListSupplier`
  (Patchy's `MojangBlockListSupplier` downloads
  `sessionserver.mojang.com/blockedservers`).
- `isAllowed(ResolvedServerAddress)` reads `getHostName()` **and**
  `getHostIp()` eagerly (even with no list loaded), then tests both against
  each list. `isAllowed(ServerAddress)` tests the typed host.
- Patchy's `BlockedServers` matches SHA-1 hashes of: the exact string, `*.`
  suffixes of a host name, and `a.b.c.*` prefixes of an IPv4 address.

So, for a literal, vanilla checks three strings: the **PTR name**, the IP and
the typed text. The PTR name can only be obtained by the reverse query; there
is no supported way to keep that leg without the query. Skipping it is
therefore a real change to one leg of the check, not an equivalence.

### 15.2 Implemented scope (safe default)

- **Fast path only for non-public literals:** loopback (`127/8`, `::1`),
  private (`10/8`, `172.16/12`, `192.168/16`), link-local (`169.254/16`,
  `fe80::/10`), carrier-grade NAT / Tailscale (`100.64/10`), IPv6
  unique-local (`fc00::/7`), and the IPv4-mapped forms of those.
- **Public literals** (`8.8.8.8`, `2001:db8::1` …) keep vanilla's reverse
  lookup and full check: unchanged security behavior. Changing them is
  Q-FIP3 (§15.5) and is **not** implemented.
- On the fast path the address is built from the literal's bytes with the
  typed text as its host name (`InetAddress.getByAddress(text, bytes)`), and
  vanilla's own `addressCheck` runs in vanilla's order on the typed text and
  the IP, then the typed address. A blocked IP is refused exactly as before.
- **Precise difference from vanilla, non-public literals only:** the PTR name
  supplied by the player's own resolver (router, hosts file, Tailscale DNS) is
  not matched against the blocklist. The typed text and IP still are. When no
  PTR record exists, vanilla itself checks the IP text as the host name, so
  the result is identical in the common case. Rationale for default-on:
  Mojang's list targets public servers, and such addresses are not reachable
  on the internet. **The PO is asked to confirm this (Q-FIP8).**
- Category **Network** ("Rede"), default on, live, client-only; only
  `ServerStatusPinger.pingServer` (server list). Join and Direct Connect
  untouched (Q-FIP2 stays deferred).

### 15.3 Code

- `IpLiteral` (Kotlin): strict parse (§6 table) and `isNonPublic`.
- `FastIpPing` (Kotlin): the fast resolution; returns null to fall back.
- `ServerStatusPingerMixin`: `@WrapOperation` on `resolveAddress` in
  `pingServer`; `ServerNameResolverAccessor` reads `addressCheck`.
- Settings, persistence and conflicts: the module's own registry and
  configuration (Q-FIP7 resolved: `core/` in Optimizations, no QoL classes).
  `fastipping` and `fastserverpings` installed → inactive (Q-FIP5).

### 15.4 Measured DNS behavior

Unit tests (`FastIpPingTest`) install a counting
`InetAddressResolverProvider` for the test JVM only: the vanilla steps on
`192.168.1.20` perform 1 reverse lookup; the fast path on IPv4/IPv6
non-public literals performs 0 forward and 0 reverse lookups, keeps the port
and runs both checks in vanilla's order; public IPs, host names and invalid
text fall back without any lookup.

Game test (`runGameTestFastipping`, real `ServerStatusPinger` against the
world opened to LAN; counting provider on the boot class path of that run
only):

| Address | Feature | Result | Reverse | Forward |
| --- | --- | --- | --- | --- |
| `127.0.0.1:<lan>` | on | status shown | 0 | 0 |
| `localhost:<lan>` | on | status shown | 0 | 1 |
| `[::1]:<lan>` | on | status shown | 0 | 0 |
| `127.0.0.1:<closed>` | on | connection refused | 0 | 0 |
| `999.1.1.1:<lan>` | on | `cannot_connect` (vanilla) | 0 | 1 |
| `127.0.0.1:<lan>` | **off** | status shown | **1** | 1 |
| `127.0.0.1:<lan>` | on again (live) | status shown | 0 | 0 |

No timing claim is made.

### 15.5 Decisions still open

- **Q-FIP8 [Open]:** confirm the non-public scope as default-on (§15.2).
- **Q-FIP3 [Open], narrowed:** public literals. Options: keep vanilla
  (current), or an opt-in setting that skips the PTR leg for public IPs too
  (weakens the blocklist's host-name leg for those addresses).
- **Q-FIP2 [Deferred]:** join and Direct Connect.
- **Q-FIP6 [Open]:** relationship to `server_ping`.
