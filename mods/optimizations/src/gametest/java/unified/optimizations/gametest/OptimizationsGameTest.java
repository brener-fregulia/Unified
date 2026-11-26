package unified.optimizations.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unified.optimizations.core.Optimizations;
import unified.optimizations.fastquit.PendingSaves;
import unified.settings.ui.FeatureSwitch;
import unified.settings.ui.UnifiedSettingsScreen;
import unified.settings.ui.WorkspaceNavigation;

/**
 * Focused client game tests for Unified: Optimizations. Scenario from {@code unified_optimizations.test.scenario}:
 * {@code fastipping} (real server-list pings against the world opened to LAN, DNS lookups counted by
 * {@link CountingDns}, shared settings) or {@code fastquit} (leave, reopen and exit worlds with background saving).
 */
public final class OptimizationsGameTest implements FabricClientGameTest {
    /** Counters of the boot-class-path DNS probe (src/dnsprobe), shared through the system properties table. */
    private static final class CountingDns {
        static final String REVERSE = "unified_optimizations.test.dns.reverse";
        static final String FORWARD = "unified_optimizations.test.dns.forward";

        static java.util.concurrent.atomic.AtomicInteger counter(String name) {
            return (java.util.concurrent.atomic.AtomicInteger) System.getProperties().computeIfAbsent(name,
                    k -> new java.util.concurrent.atomic.AtomicInteger());
        }

        static void reset() {
            counter(REVERSE).set(0);
            counter(FORWARD).set(0);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("unified_optimizations_gametest");
    private static final String FAST_IP_PING = "fast_ip_ping";
    private static final String FAST_QUIT = "fast_quit";
    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        ctx.waitForScreen(TitleScreen.class);
        String scenario = System.getProperty("unified_optimizations.test.scenario", "fastipping");
        if (scenario.equals("fastquit")) {
            fastQuit();
        } else {
            settings();
            fastIpPing();
        }
        LOGGER.info("[optimizations-test] {} passed", scenario);
    }

    // ---- Shared settings (Optimizations-only workspace) ----

    private void settings() {
        ctx.setScreen(() -> new UnifiedSettingsScreen(new TitleScreen()));
        ctx.waitForScreen(UnifiedSettingsScreen.class);
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            var screen = (UnifiedSettingsScreen) mc.gui.screen();
            var categories = screen.navRows().stream().filter(r -> r.getKind() == WorkspaceNavigation.Kind.CATEGORY)
                    .map(WorkspaceNavigation.Row::getCategoryId).toList();
            check(categories.equals(java.util.List.of("network", "performance")), "categories " + categories);
            check(screen.navRows().stream().noneMatch(r -> r.getKind() == WorkspaceNavigation.Kind.MODULE),
                    "one module: no module row");
            check(FAST_IP_PING.equals(screen.navigation().selected()), "Fast IP Ping first");
        });
        ctx.takeScreenshot("optimizations-settings-fast-ip-ping");
        ctx.runOnClient(mc -> ((UnifiedSettingsScreen) mc.gui.screen()).select("unified_optimizations", FAST_QUIT));
        ctx.waitTicks(2);
        boolean before = ctx.computeOnClient(mc -> Optimizations.isEnabled(FAST_QUIT));
        ctx.runOnClient(mc -> switchOf(mc).onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0)));
        ctx.waitTicks(2);
        check(Optimizations.isEnabled(FAST_QUIT) != before, "the switch applies at once");
        check(read(configFile()).contains("\"fast_quit\""), "and is saved to config/unified_optimizations");
        ctx.takeScreenshot("optimizations-settings-fast-quit");
        ctx.runOnClient(mc -> switchOf(mc).onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0)));
        ctx.waitTicks(2);
        check(Optimizations.isEnabled(FAST_QUIT) == before, "switched back");
        ctx.setScreen(TitleScreen::new);
    }

    private static FeatureSwitch switchOf(Minecraft mc) {
        return (FeatureSwitch) mc.gui.screen().children().stream().filter(FeatureSwitch.class::isInstance)
                .findFirst().orElseThrow();
    }

    // ---- Fast IP Ping (fast-ip-ping-v1.md §11) ----

    private void fastIpPing() {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            int port = world.getServer().computeOnServer(s -> {
                int p = HttpUtil.getAvailablePort();
                check(((IntegratedServer) s).publishServer(MinecraftServer.MultiplayerScope.LAN, p), "opened to LAN");
                return p;
            });
            int closed = HttpUtil.getAvailablePort();
            LOGGER.info("[optimizations-test] LAN port {}, closed port {}", port, closed);
            check(Optimizations.isActive(FAST_IP_PING), "on by default");

            Result ipv4 = ping("127.0.0.1:" + port);
            check(ipv4.ok, "IPv4 literal pinged: " + ipv4);
            check(ipv4.reverse == 0, "IPv4 literal: no reverse lookup, saw " + ipv4.reverse);

            Result host = ping("localhost:" + port);
            check(host.ok, "hostname pinged: " + host);
            check(host.reverse == 0, "hostname: no reverse lookup");

            Result ipv6 = ping("[::1]:" + port);
            check(ipv6.reverse == 0, "IPv6 literal: no reverse lookup");
            if (!ipv6.ok) {
                LOGGER.warn("[optimizations-test] IPv6 loopback not reachable here (skipped): {}", ipv6);
            }

            Result unreachable = ping("127.0.0.1:" + closed);
            check(!unreachable.ok && unreachable.reverse == 0, "unreachable literal fails, no lookup: " + unreachable);

            Result invalid = ping("999.1.1.1:" + port);
            check(!invalid.ok, "invalid address fails as in vanilla: " + invalid);

            // Off: vanilla's reverse lookup comes back for the same literal (live, no restart).
            check(Optimizations.setEnabled(FAST_IP_PING, false) == null, "switched off");
            Result vanilla = ping("127.0.0.1:" + port);
            check(vanilla.ok, "vanilla ping still works: " + vanilla);
            check(vanilla.reverse >= 1, "vanilla reverse-resolves the literal, saw " + vanilla.reverse);
            check(Optimizations.setEnabled(FAST_IP_PING, true) == null, "switched on again");
            Result again = ping("127.0.0.1:" + port);
            check(again.ok && again.reverse == 0, "on again: no reverse lookup: " + again);
        }
    }

    private record Result(boolean ok, String detail, int reverse, int forward) {
    }

    /** One real server-list ping, as the multiplayer screen does it: resolve and connect off-thread, tick on-thread. */
    private Result ping(String ip) {
        CountingDns.reset();
        ServerData data = new ServerData("test", ip, ServerData.Type.OTHER);
        ServerStatusPinger pinger = new ServerStatusPinger();
        AtomicBoolean done = new AtomicBoolean();
        AtomicBoolean success = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                pinger.pingServer(data, () -> { }, () -> {
                    success.set(true);
                    done.set(true);
                }, EventLoopGroupHolder.remote(false));
            } catch (Exception e) {
                failure.set(e.toString());
                done.set(true);
            }
        }, "test pinger");
        thread.start();
        ctx.waitFor(mc -> {
            pinger.tick();
            return done.get() || failedMotd(data) != null;
        }, 400);
        ctx.runOnClient(mc -> pinger.removeAll());
        String detail = success.get() ? "ok " + data.motd.getString()
                : failure.get() != null ? failure.get() : String.valueOf(failedMotd(data));
        Result result = new Result(success.get(), detail, CountingDns.counter(CountingDns.REVERSE).get(),
                CountingDns.counter(CountingDns.FORWARD).get());
        LOGGER.info("[optimizations-test] ping {} → {}", ip, result);
        return result;
    }

    private static String failedMotd(ServerData data) {
        Component motd = data.motd;
        return motd != null && motd.getContents() instanceof TranslatableContents t
                && t.getKey().startsWith("multiplayer.status.cannot") ? t.getKey() : null;
    }

    // ---- FastQuit (fastquit-v1.md §9) ----

    private void fastQuit() {
        check(Optimizations.setEnabled(FAST_QUIT, true) == null, "FastQuit enabled");
        int tracked = PendingSaves.getTrackedCount();
        String levelId;
        Path worldDir;
        // 1. Leave through the normal path (Fabric's close calls Minecraft.disconnect, like Save and Quit).
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("setblock 0 120 0 minecraft:gold_block");
            worldDir = world.getServer().computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).toAbsolutePath()
                    .normalize());
            levelId = worldDir.getFileName().toString();
        }
        check(PendingSaves.getTrackedCount() == tracked + 1, "the shutdown was tracked instead of awaited");
        ctx.waitFor(mc -> !PendingSaves.hasPending(), 1200);
        check(!locked(worldDir), "session.lock released after the background save");
        ctx.waitTicks(2);
        ctx.takeScreenshot("fastquit-after-leave");

        // 2. Reopen, change the world, then leave and reopen at once: the reopen waits for the save.
        open(levelId);
        ctx.runOnClient(mc -> {
            check(block(mc, 0, 120, 0) == Blocks.GOLD_BLOCK, "the first session's block was saved");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "setblock 0 121 0 minecraft:diamond_block"));
        });
        ctx.waitFor(mc -> block(mc, 0, 121, 0) == Blocks.DIAMOND_BLOCK, 200);
        AtomicBoolean pendingRightAfterLeave = new AtomicBoolean();
        // Leave, then open the same world in the very next client task. (The game-test harness defers
        // Minecraft.disconnect past the task that calls it, so the test waits until the client has left.)
        ctx.runOnClient(OptimizationsGameTest::leave);
        ctx.waitFor(mc -> mc.level == null, 200); // the client has left; the server may still be saving
        pendingRightAfterLeave.set(PendingSaves.hasPending());
        LOGGER.info("[optimizations-test] still saving when the menu came back: {}", pendingRightAfterLeave.get());
        // The harness runs the server in lockstep with this thread, so a render-thread wait for the save (as vanilla's
        // own) cannot complete inside it; the save finishes while the harness ticks, then the world reopens.
        // The wait-before-open rule itself is covered by PendingSavesTest and the manual QA list.
        ctx.waitFor(mc -> !PendingSaves.hasPending(), 1200);
        check(!locked(worldDir), "lock released before reopening");
        open(levelId);
        check(PendingSaves.getTrackedCount() == tracked + 2, "second shutdown tracked");
        ctx.runOnClient(mc -> check(block(mc, 0, 121, 0) == Blocks.DIAMOND_BLOCK,
                "the reopened world contains the change made just before leaving"));
        ctx.takeScreenshot("fastquit-reopened");

        // 3. Off: vanilla waits; nothing is tracked.
        check(Optimizations.setEnabled(FAST_QUIT, false) == null, "FastQuit disabled");
        ctx.runOnClient(mc -> mc.execute(() -> leave(mc)));
        ctx.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, 1200);
        check(PendingSaves.getTrackedCount() == tracked + 2 && !PendingSaves.hasPending(), "nothing tracked when off");
        ctx.waitFor(mc -> !locked(worldDir), 1200); // the harness defers vanilla's own wait as well

        check(Optimizations.setEnabled(FAST_QUIT, true) == null, "FastQuit enabled again");
        ctx.setScreen(TitleScreen::new);
    }

    private void open(String levelId) {
        ctx.runOnClient(mc -> mc.execute(() -> mc.createWorldOpenFlows().openWorld(levelId, () -> { })));
        waitInWorld();
    }

    private void waitInWorld() {
        ctx.waitFor(mc -> mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                && mc.gui.screen() == null, 2400);
        ctx.waitTicks(20);
    }

    /** What Save and Quit does: disconnect the level, then {@code Minecraft.disconnect}. */
    private static void leave(Minecraft mc) {
        mc.level.disconnect(Component.translatable("menu.savingLevel"));
        mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
    }

    /** Reads the block from the client world (the server runs in lockstep with the test thread). */
    private static net.minecraft.world.level.block.Block block(Minecraft mc, int x, int y, int z) {
        return mc.level.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    private static boolean locked(Path worldDir) {
        try {
            return DirectoryLock.isLocked(worldDir);
        } catch (java.nio.channels.OverlappingFileLockException e) {
            return true; // held by this JVM (the integrated server)
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("unified_optimizations").resolve("config.json");
    }

    private static String read(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @SuppressWarnings("unused")
    private static int widgets(Minecraft mc) {
        return Screens.getWidgets(mc.gui.screen()).size();
    }
}
