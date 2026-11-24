package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.mixin.SoundEngineAccessor;
import io.github.brener_fregulia.qolunified.mixin.SoundManagerAccessor;
import io.github.brener_fregulia.qolunified.ui.FeatureActions;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Minecraft side of Sound Control (sound-control-v1.md §5, §7): the rule table read by
 * {@code SoundEngineVolumeMixin}, the recent-sounds listener and the two entry points.
 *
 * <p>The table is immutable and published through a volatile field, so the audio path may read it on any thread
 * without locks or allocation. Everything else runs on the client thread.
 */
public final class SoundControlHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");

    private static volatile Map<Identifier, Float> table = Map.of();
    private static volatile boolean failureLogged;

    private static final RecentSounds<Identifier, SoundSource> RECENT =
            new RecentSounds<>(SoundControlSettings.RECENT_CAPACITY);
    // Vanilla notifies listeners before its zero-volume check, so muted sounds are recorded too (§3.2).
    private static final SoundEventListener LISTENER = (sound, events, range) ->
            RECENT.record(sound.getIdentifier(), sound.getSource(), System.nanoTime() / 1_000_000L);
    private static boolean historyWanted;
    private static SoundManager listeningTo;

    private SoundControlHooks() {
    }

    /** The gain factor for {@code sound}: 1 without a rule or while disabled, else the rule's factor (0–1). */
    public static float factor(SoundInstance sound) {
        Map<Identifier, Float> current = table;
        if (current.isEmpty()) {
            return 1f;
        }
        try {
            return SoundRules.factor(current, sound.getIdentifier());
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.error("Feature {}: rule lookup failed; the sound plays at vanilla volume", SoundControlSettings.ID,
                        e);
            }
            return 1f;
        }
    }

    /** Installed on the first enable. */
    static void install(SoundControlFeature feature) {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            attachHistory(minecraft);
            feature.sync();
        });
        FeatureActions.register(SoundControlSettings.ID, new FeatureActions.Action(SoundControlTexts.OPEN,
                (parent, draft) -> new SoundControlScreen(parent, draft, false)));
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof SoundOptionsScreen && SoundControlFeature.activeSettings() != null) {
                addOptionsButton(minecraft, screen);
            }
        });
    }

    /** Replaces the table, then lets vanilla recompute the gain of every playing sound (§5.2). Client thread. */
    static void publish(Map<String, SoundRule> rules) {
        table = SoundRules.table(rules, Identifier::tryParse);
        SoundManager sounds = soundManager();
        if (sounds != null) {
            sounds.refreshCategoryVolume(SoundSource.MASTER);
        }
    }

    static void startHistory() {
        historyWanted = true;
        attachHistory(Minecraft.getInstance());
    }

    static void stopHistory() {
        historyWanted = false;
        if (listeningTo != null) {
            listeningTo.removeListener(LISTENER);
            listeningTo = null;
        }
        RECENT.clear();
    }

    /** Most recently heard first; empty while disabled. */
    public static List<RecentSounds.Entry<Identifier, SoundSource>> recent() {
        return RECENT.snapshot();
    }

    /** False when Minecraft has no audio device; vanilla then plays and reports nothing. */
    public static boolean audioAvailable() {
        SoundManager sounds = soundManager();
        return sounds != null
                && ((SoundEngineAccessor) ((SoundManagerAccessor) sounds).qolUnified$soundEngine()).qolUnified$loaded();
    }

    /** The factor currently published for {@code id}; for tests and the screen. */
    public static float publishedFactor(Identifier id) {
        return SoundRules.factor(table, id);
    }

    /** Opens the editor over {@code parent} with its own draft; its Done saves and applies (§6.4). */
    public static void openStandalone(Minecraft minecraft, Screen parent) {
        minecraft.gui.setScreen(new SoundControlScreen(parent, new SettingsDraft(QolUnifiedClient.core()), true));
    }

    // The listener is attached on the client thread once the sound manager exists; it may not during client start.
    private static void attachHistory(Minecraft minecraft) {
        if (!historyWanted || listeningTo != null || minecraft == null) {
            return;
        }
        SoundManager sounds = minecraft.getSoundManager();
        if (sounds != null) {
            sounds.addListener(LISTENER);
            listeningTo = sounds;
        }
    }

    private static SoundManager soundManager() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getSoundManager();
    }

    /** Adds "Sound Control…" as a row of vanilla's Music & Sound options list, so it scrolls with the list. */
    private static void addOptionsButton(Minecraft minecraft, Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof OptionsList list) {
                list.addSmall(List.of(Button.builder(Component.translatable(SoundControlTexts.OPEN_FROM_OPTIONS),
                        b -> openStandalone(minecraft, screen)).build()));
                return;
            }
        }
    }
}
