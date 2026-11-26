package unified.qol.feature.soundcontrol;

import unified.qol.QolUnifiedClient;
import unified.qol.mixin.SoundEngineAccessor;
import unified.qol.mixin.SoundManagerAccessor;
import unified.qol.ui.FeatureActions;
import unified.qol.ui.SettingsDraft;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>Two immutable tables are published through volatile fields, so the audio path may read them on any thread
 * without locks or allocation: the live preview overrides of the screen being edited (§16), then the committed
 * rules. While the feature is disabled both are empty and every sound plays at vanilla volume. Everything else runs
 * on the client thread.
 */
public final class SoundControlHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");

    private static volatile Map<Identifier, Float> table = Map.of();
    private static volatile Map<Identifier, Float> preview = Map.of();
    private static volatile boolean failureLogged;

    private static final RecentSounds<Identifier, SoundSource> RECENT =
            new RecentSounds<>(SoundControlSettings.RECENT_CAPACITY);
    // Vanilla notifies listeners before its zero-volume check, so muted sounds are recorded too (§3.2).
    private static final SoundEventListener LISTENER = (sound, events, range) ->
            RECENT.record(sound.getIdentifier(), sound.getSource(), System.nanoTime() / 1_000_000L);
    private static boolean historyWanted;
    private static SoundManager listeningTo;

    // Client thread only.
    private static SoundControlFeature feature;
    private static boolean started;
    private static Map<String, SoundRule> committedRules = Map.of();
    /** The rules being previewed (the draft or the screen's working copy), or null without a preview. */
    private static Map<String, SoundRule> previewTarget;
    /** The draft whose edits are previewed; the preview ends when no screen of this draft is shown. */
    private static SettingsDraft previewDraft;
    /** The draft screen last seen by {@link #followDraftScreen}, so an unchanged screen is not re-examined. */
    private static SettingsDraft followed;

    private SoundControlHooks() {
    }

    /** The gain factor for {@code sound}: 1 without a rule or while disabled, else the rule's factor (0–1). */
    public static float factor(SoundInstance sound) {
        Map<Identifier, Float> overrides = preview;
        Map<Identifier, Float> current = table;
        if (overrides.isEmpty() && current.isEmpty()) {
            return 1f;
        }
        try {
            Identifier id = sound.getIdentifier();
            if (!overrides.isEmpty()) {
                Float override = overrides.get(id);
                if (override != null) {
                    return override;
                }
            }
            return SoundRules.factor(current, id);
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
    static void install(SoundControlFeature owner) {
        feature = owner;
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            attachHistory(minecraft);
            owner.sync();
            followDraftScreen(minecraft);
        });
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof SoundOptionsScreen && SoundControlFeature.activeSettings() != null) {
                addOptionsButton(minecraft, screen);
            }
        });
    }

    /** The settings panel's "Open Sound Control" tool, registered at client start (settings-ux-v1.md Q-SU6). */
    public static void registerAction() {
        FeatureActions.register(SoundControlSettings.ID, new FeatureActions.Action(SoundControlTexts.OPEN,
                (parent, draft) -> new SoundControlScreen(parent, draft, false)));
    }

    /** Replaces the table, then lets vanilla recompute the gain of every playing sound (§5.2). Client thread. */
    static void publish(Map<String, SoundRule> rules) {
        committedRules = rules;
        table = SoundRules.table(rules, Identifier::tryParse);
        if (previewTarget != null) {
            preview = SoundRules.keyed(SoundRules.overrides(previewTarget, committedRules), Identifier::tryParse);
        }
        refresh();
    }

    static void start() {
        started = true;
        historyWanted = true;
        attachHistory(Minecraft.getInstance());
    }

    static void stop() {
        started = false;
        endPreview();
        historyWanted = false;
        if (listeningTo != null) {
            listeningTo.removeListener(LISTENER);
            listeningTo = null;
        }
        RECENT.clear();
    }

    // ---- Live preview (§16) ----

    /** Previews {@code rules} as the audible state while {@code owner} is being edited. Nothing is saved. */
    static void preview(SettingsDraft owner, Map<String, SoundRule> rules) {
        if (!started) {
            return;
        }
        previewDraft = owner;
        previewTarget = new HashMap<>(rules);
        setPreview(SoundRules.keyed(SoundRules.overrides(previewTarget, committedRules), Identifier::tryParse));
    }

    /** Previews the draft's own value, dropping a screen's unconfirmed edits. */
    static void previewDraft(SettingsDraft owner) {
        preview(owner, SoundRules.read(owner.choice(SoundControlSettings.ID).settings().get(SoundControlSettings.RULES)));
    }

    /**
     * Previews one changed rule. Copies only the (small) override table, and refreshes playing sounds only when the
     * effective factor of that event actually changed.
     */
    static void previewRule(String id, SoundRule rule) {
        if (previewTarget == null) {
            return;
        }
        if (rule.isDefault()) {
            previewTarget.remove(id);
        } else {
            previewTarget.put(id, rule);
        }
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return;
        }
        Float factor = SoundRules.override(rule, committedRules.get(id));
        Map<Identifier, Float> current = preview;
        if (Objects.equals(current.get(key), factor)) {
            return;
        }
        var next = new HashMap<>(current);
        if (factor == null) {
            next.remove(key);
        } else {
            next.put(key, factor);
        }
        preview = next.isEmpty() ? Map.of() : Collections.unmodifiableMap(next);
        refresh();
    }

    /** Ends the preview; the committed rules (or vanilla, when disabled) are audible again. */
    static void endPreview() {
        previewDraft = null;
        previewTarget = null;
        setPreview(Map.of());
    }

    /** Publishes newly committed settings now instead of on the next tick (standalone Done). */
    static void syncNow() {
        if (feature != null) {
            feature.sync();
        }
    }

    /**
     * Keeps the preview tied to the draft on screen. When the shown screen edits another draft, that draft's value is
     * previewed; when no draft screen is shown (Done or Cancel of the parent settings, a disconnect, any other screen),
     * the preview ends, so a stale preview cannot survive its transaction.
     */
    private static void followDraftScreen(Minecraft minecraft) {
        if (!started) {
            return;
        }
        SettingsDraft shown = minecraft.gui.screen() == null ? null
                : unified.qol.ui.screen.QolWorkspace.draftShown(minecraft.gui.screen());
        if (shown == previewDraft || shown == followed) {
            return;
        }
        followed = shown;
        if (shown == null) {
            endPreview();
            return;
        }
        Map<String, SoundRule> rules = SoundRules.read(
                shown.choice(SoundControlSettings.ID).settings().get(SoundControlSettings.RULES));
        if (rules.equals(committedRules)) {
            // The auto-applying settings workspace holds only committed rules: nothing to preview.
            endPreview();
        } else {
            preview(shown, rules);
        }
    }

    private static void setPreview(Map<Identifier, Float> next) {
        if (next.equals(preview)) {
            return;
        }
        preview = next;
        refresh();
    }

    /** Vanilla recomputes the gain of every playing sound, as its own volume sliders do. */
    private static void refresh() {
        SoundManager sounds = soundManager();
        if (sounds != null) {
            sounds.refreshCategoryVolume(SoundSource.MASTER);
        }
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

    /** The committed factor for {@code id}, ignoring any preview; for tests. */
    public static float publishedFactor(Identifier id) {
        return SoundRules.factor(table, id);
    }

    /** The audible factor for {@code id}: the preview when present, else the committed rule; for tests. */
    public static float effectiveFactor(Identifier id) {
        Float override = preview.get(id);
        return override != null ? override : SoundRules.factor(table, id);
    }

    public static boolean previewing() {
        return previewTarget != null;
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
