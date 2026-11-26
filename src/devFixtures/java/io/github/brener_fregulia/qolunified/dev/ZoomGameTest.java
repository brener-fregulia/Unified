package io.github.brener_fregulia.qolunified.dev;

import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapHooks;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapScreen;
import io.github.brener_fregulia.qolunified.feature.zoom.ZoomHooks;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only client game test for Zoom (zoom-v1.md §17), in a disposable singleplayer world inside
 * {@code build/run-gametest-zoom}. FOV values are read from the camera and logged as evidence.
 */
final class ZoomGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");
    private static final String ID = "zoom";

    private final ClientGameTestContext ctx;
    private TestServerContext server;
    private float vanillaFov;

    ZoomGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        on(core -> check(core.lifecycle().state(ID) == FeatureState.ACTIVE, "enabled by default"));
        keyRegistration();
        Path options = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        double[] before = ctx.computeOnClient(mc -> new double[] {mc.options.fov().get(),
                mc.options.sensitivity().get()});
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0.5 100 0.5 0 0");
            ctx.runOnClient(mc -> mc.options.save());
            byte[] optionsBefore = read(options);
            ctx.waitTicks(5);
            vanillaFov = fov();
            log("vanilla FOV", vanillaFov);
            holdAndRelease();
            scroll();
            sensitivity();
            perspectives();
            spyglass();
            screens();
            settings();
            disableWhileZoomed();
            keyboardMap();
            double[] after = ctx.computeOnClient(mc -> new double[] {mc.options.fov().get(),
                    mc.options.sensitivity().get()});
            check(Arrays.equals(before, after), "Z-AC-8: FOV and sensitivity options unchanged");
            ctx.runOnClient(mc -> mc.options.save());
            check(Arrays.equals(optionsBefore, read(options)), "options.txt identical after zoom cycles");
        }
    }

    // Z-AC-13 (part), localization (Z-AC-15).
    private void keyRegistration() {
        ctx.runOnClient(mc -> {
            check(ZoomHooks.key() != null, "zoom key registered");
            check(Arrays.asList(mc.options.keyMappings).contains(ZoomHooks.key()), "listed in vanilla Controls");
            check(ZoomHooks.key().getDefaultKey().getValue() == InputConstants.KEY_Z, "default: physical Z key");
            for (String key : new String[] {"qol_unified.key.zoom", "qol_unified.feature.zoom.name",
                    "qol_unified.feature.zoom.setting.max_magnification",
                    "qol_unified.feature.zoom.error.min_above_initial"}) {
                check(!I18n.get(key).equals(key), "translated: " + key);
            }
        });
    }

    // Z-AC-1, Z-AC-3, Z-AC-8.
    private void holdAndRelease() {
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(1);
        float mid = fov();
        ctx.waitTicks(10);
        float zoomed = fov();
        log("holding, after 1 tick / 11 ticks", mid, zoomed);
        check(state().equals("ZOOMED"), "zoomed while held, was " + state());
        check(Math.abs(zoomed - vanillaFov / 4) < 0.5, "Z-AC-1: 4× → " + zoomed);
        check(mid <= vanillaFov && mid >= zoomed, "transition between the endpoints");
        shot("01-zoomed-4x");
        ctx.getInput().releaseKey(ZoomHooks.key());
        ctx.waitTicks(8);
        check(state().equals("IDLE"), "released, was " + state());
        check(Math.abs(fov() - vanillaFov) < 0.01, "Z-AC-8: vanilla FOV back → " + fov());
    }

    // Z-AC-5, Z-AC-6.
    private void scroll() {
        int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(10);
        ctx.getInput().scroll(1);
        ctx.waitTicks(1);
        check(Math.abs(ZoomHooks.level() - 5) < 1e-6, "one notch up: 5×, was " + ZoomHooks.level());
        ctx.getInput().scroll(-0.5);
        ctx.waitTicks(1);
        check(Math.abs(ZoomHooks.level() - 5 / Math.sqrt(1.25)) < 1e-6, "half notch down keeps its magnitude");
        for (int i = 0; i < 30; i++) {
            ctx.getInput().scroll(1);
        }
        ctx.waitTicks(10);
        check(Math.abs(ZoomHooks.level() - 16) < 1e-6, "upper bound 16×");
        log("FOV at 16×", fov());
        shot("02-zoomed-16x");
        check(slot == (int) ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()),
                "Z-AC-6: hotbar unchanged while zoomed");
        ctx.getInput().releaseKey(ZoomHooks.key());
        ctx.waitTicks(8);
        ctx.getInput().scroll(-1);
        ctx.waitTicks(1);
        check(slot != (int) ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()),
                "the wheel scrolls the hotbar again after zoom");
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(2);
        check(Math.abs(ZoomHooks.level() - 4) < 1e-6, "Q-Z6: next activation starts at 4×");
        ctx.getInput().releaseKey(ZoomHooks.key());
        ctx.waitTicks(8);
    }

    // Z-AC-7: the same cursor movement turns a quarter as far at 4×.
    private void sensitivity() {
        turn(); // the first cursor event only sets the starting position
        float plain = turn();
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(10);
        float zoomed = turn();
        ctx.getInput().releaseKey(ZoomHooks.key());
        ctx.waitTicks(8);
        log("yaw change for 100 px: plain / zoomed", plain, zoomed);
        check(plain != 0, "the cursor turns the player");
        check(Math.abs(zoomed / plain - 0.25) < 0.0125, "a quarter at 4× (±5 %), was " + zoomed / plain);
    }

    private float turn() {
        float start = ctx.computeOnClient(mc -> mc.player.getYRot());
        ctx.getInput().moveCursor(100, 0);
        ctx.waitTicks(2);
        float end = ctx.computeOnClient(mc -> mc.player.getYRot());
        server.runCommand("tp @a 0.5 100 0.5 0 0");
        ctx.waitTicks(2);
        return end - start;
    }

    // Z-AC-11.
    private void perspectives() {
        for (CameraType type : new CameraType[] {CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT}) {
            ctx.runOnClient(mc -> mc.options.setCameraType(type));
            ctx.waitTicks(2);
            float plain = fov();
            ctx.getInput().holdKey(ZoomHooks.key());
            ctx.waitTicks(10);
            check(Math.abs(fov() - plain / 4) < 0.5, type + ": 4× → " + fov());
            shot("03-" + type.name().toLowerCase(java.util.Locale.ROOT));
            ctx.getInput().releaseKey(ZoomHooks.key());
            ctx.waitTicks(8);
        }
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(2);
    }

    // Z-AC-12.
    private void spyglass() {
        server.runCommand("item replace entity @a weapon.mainhand with spyglass");
        ctx.waitTicks(2);
        ctx.getInput().holdKey(o -> o.keyUse);
        ctx.waitTicks(10);
        float scoping = fov();
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(10);
        float both = fov();
        log("spyglass / spyglass + zoom", scoping, both);
        check(scoping < vanillaFov * 0.2, "spyglass narrows the FOV itself");
        check(both < scoping && both >= 1.0f, "zoom multiplies on top, floor 1°");
        shot("04-spyglass-and-zoom");
        ctx.getInput().releaseKey(ZoomHooks.key());
        ctx.getInput().releaseKey(o -> o.keyUse);
        server.runCommand("item replace entity @a weapon.mainhand with air");
        ctx.waitTicks(30); // vanilla eases its own spyglass FOV modifier back over several ticks
        check(Math.abs(fov() - vanillaFov) < 0.01, "both released");
    }

    // Z-AC-9 and Q-Z10: hold and toggle both end when a menu opens.
    private void screens() {
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(10);
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(1);
        check(!state().equals("ZOOMED"), "menu ends hold zoom");
        ctx.getInput().releaseKey(ZoomHooks.key());
        ctx.setScreen(() -> null);
        ctx.waitTicks(8);
        check(state().equals("IDLE"), "not zoomed after the menu");

        setSetting("zoom_mode", "toggle");
        ctx.getInput().pressKey(ZoomHooks.key());
        ctx.waitTicks(10);
        check(state().equals("ZOOMED"), "toggle zooms with the key released");
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(2);
        ctx.setScreen(() -> null);
        ctx.waitTicks(8);
        check(state().equals("IDLE"), "Q-Z10: toggle does not come back after a menu");
        setSetting("zoom_mode", "hold");
    }

    // Z-AC-14: LIVE settings through the workspace draft, persisted.
    private void settings() {
        setSetting("initial_magnification", 8);
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(10);
        check(Math.abs(fov() - vanillaFov / 8) < 0.5, "new initial magnification applies at once → " + fov());
        ctx.getInput().releaseKey(ZoomHooks.key());
        ctx.waitTicks(8);
        on(core -> {
            var draft = new SettingsDraft(core);
            check(draft.ruleViolation(ID, "max_magnification", 4).isPresent(), "max below initial rejected");
            check(draft.setValue(ID, "max_magnification", 4).isPresent() && !draft.valid(), "not in the draft");
        });
        check(config().contains("\"initial_magnification\": 8"), "persisted");
        check(!config().contains("\"max_magnification\": 4"), "the contradiction never reached the file");
        setSetting("initial_magnification", 4);
    }

    // Z-AC-10.
    private void disableWhileZoomed() {
        ctx.getInput().holdKey(ZoomHooks.key());
        ctx.waitTicks(10);
        check(state().equals("ZOOMED"), "zoomed before disabling");
        setEnabled(false);
        ctx.waitTicks(1);
        check(Math.abs(fov() - vanillaFov) < 0.01, "FOV back at once → " + fov());
        int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        ctx.getInput().scroll(-1);
        ctx.waitTicks(1);
        check(slot != (int) ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()),
                "the wheel is vanilla while disabled");
        ctx.getInput().releaseKey(ZoomHooks.key());
        setEnabled(true);
    }

    // Z-AC-13: Keyboard Map lists the binding on Z.
    private void keyboardMap() {
        ctx.runOnClient(mc -> KeyboardMapHooks.open(mc, null));
        ctx.waitForScreen(KeyboardMapScreen.class);
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            var screen = (KeyboardMapScreen) mc.gui.screen();
            var binding = screen.catalog().binding(ZoomHooks.KEY_ZOOM);
            check(binding.isPresent(), "Keyboard Map lists Zoom");
            check(binding.get().inputLabel().equalsIgnoreCase("Z"), "on Z, was " + binding.get().inputLabel());
        });
        shot("05-keyboard-map");
        ctx.setScreen(() -> null);
        ctx.waitTicks(2);
    }

    // ---- Helpers ----

    private float fov() {
        return ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
    }

    private String state() {
        return ctx.computeOnClient(mc -> ZoomHooks.state());
    }

    private void setSetting(String key, Object value) {
        on(core -> {
            var choice = core.choices().get(ID);
            core.saveAndApply(Map.of(ID, choice.withSettings(choice.settings().with(key, value))));
        });
        ctx.waitTicks(1);
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of(ID, core.choices().get(ID).withEnabled(enabled))));
        on(core -> check(core.lifecycle().state(ID) == (enabled ? FeatureState.ACTIVE : FeatureState.INACTIVE),
                "state after toggle"));
    }

    private String config() {
        try {
            return Files.readString(FabricLoader.getInstance().getConfigDir().resolve("qol_unified/config.json"));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void on(Consumer<CoreRuntime> assertion) {
        ctx.runOnClient(mc -> assertion.accept(QolUnifiedClient.core()));
    }

    private void shot(String name) {
        ctx.takeScreenshot("zoom-" + name);
    }

    private static void log(String what, Object... values) {
        LOGGER.info("[zoom] {}: {}", what, Arrays.toString(values));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
