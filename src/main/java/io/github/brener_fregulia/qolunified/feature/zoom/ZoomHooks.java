package io.github.brener_fregulia.qolunified.feature.zoom;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.core.FeatureContext;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.ui.screen.SettingsKeyBinding;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

/**
 * Glue between Minecraft and {@link ZoomController} (zoom-v1.md §5, §12): the key mapping, per-tick reset checks and
 * the entry points the three Mixins call. Everything runs on the client thread.
 */
public final class ZoomHooks {
    public static final String KEY_ZOOM = "qol_unified.key.zoom";

    private static final ZoomController CONTROLLER = new ZoomController();
    private static KeyMapping key;
    private static SettingsSnapshot configuredFrom;
    private static LocalPlayer lastPlayer;
    private static ClientLevel lastLevel;

    private ZoomHooks() {
    }

    /** Registers the Zoom key (default: the physical Z key, Q-Z2) and the tick and disconnect hooks. */
    public static void register() {
        if (key != null) {
            return;
        }
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_ZOOM, InputConstants.Type.KEYBOARD,
                InputConstants.KEY_Z, SettingsKeyBinding.category()));
        ClientTickEvents.END_CLIENT_TICK.register(ZoomHooks::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> CONTROLLER.reset());
    }

    /** The Zoom key mapping; for development game tests and Keyboard Map checks. */
    public static KeyMapping key() {
        return key;
    }

    static void featureDisabled() {
        CONTROLLER.reset();
        configuredFrom = null;
    }

    private static void tick(Minecraft minecraft) {
        if (!refresh()) {
            while (key.consumeClick()) {
                // presses while the feature is off are dropped, not queued for later
            }
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            CONTROLLER.reset();
            lastPlayer = null;
            lastLevel = null;
            return;
        }
        boolean blocked = minecraft.gui.screen() != null || !minecraft.isWindowActive() || minecraft.isPaused()
                || minecraft.player.isDeadOrDying()
                || (lastPlayer != null && minecraft.player != lastPlayer) // respawn creates a new player
                || (lastLevel != null && minecraft.level != lastLevel); // dimension change
        lastPlayer = minecraft.player;
        lastLevel = minecraft.level;
        while (key.consumeClick()) {
            if (!blocked) {
                CONTROLLER.togglePressed();
            }
        }
        if (blocked) {
            CONTROLLER.release(); // Q-Z10: also ends toggle mode, so zoom never stays on behind a menu
        } else {
            CONTROLLER.hold(key.isDown());
        }
    }

    /** Picks up LIVE settings changes; returns false while the feature is inactive. */
    private static boolean refresh() {
        FeatureContext context = ZoomFeature.active();
        if (context == null) {
            return false;
        }
        SettingsSnapshot settings = context.settings();
        if (settings != configuredFrom) { // a reference check per call; the snapshot changes only on commit
            configuredFrom = settings;
            CONTROLLER.configure(ZoomConfig.from(settings));
        }
        return true;
    }

    // ---- Mixin entry points ----

    /** Camera.calculateFov: the once-per-frame point where the animation advances and the FOV is divided. */
    public static float fov(float vanillaFov) {
        if (ZoomFeature.active() == null) {
            return vanillaFov;
        }
        if (CONTROLLER.idle()) {
            CONTROLLER.advance(Util.getNanos()); // keeps the clock current, nothing else
            return vanillaFov;
        }
        refresh();
        if (CONTROLLER.config().toggle() == false && key != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() == null && minecraft.isWindowActive()) {
                CONTROLLER.hold(key.isDown()); // a release between ticks ends the zoom this frame
            }
        }
        CONTROLLER.advance(Util.getNanos());
        return (float) CONTROLLER.fov(vanillaFov);
    }

    /** MouseHandler.onScroll, no screen open: whether zoom takes this wheel event. */
    public static boolean scroll(double vertical, boolean discrete) {
        if (ZoomFeature.active() == null) {
            return false;
        }
        return CONTROLLER.scroll(discrete ? Math.signum(vertical) : vertical);
    }

    /** MouseHandler.turnPlayer: multiplier for the turn deltas. */
    public static double sensitivity() {
        if (ZoomFeature.active() == null || CONTROLLER.idle()) {
            return 1;
        }
        return CONTROLLER.sensitivityScale();
    }

    /** For development game tests. */
    public static String state() {
        return CONTROLLER.state().name();
    }

    /** For development game tests. */
    public static double level() {
        return CONTROLLER.level();
    }
}
