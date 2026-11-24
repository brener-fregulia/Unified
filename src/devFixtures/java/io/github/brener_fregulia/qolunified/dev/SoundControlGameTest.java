package io.github.brener_fregulia.qolunified.dev;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.core.config.FeatureChoice;
import io.github.brener_fregulia.qolunified.feature.soundcontrol.SoundControlHooks;
import io.github.brener_fregulia.qolunified.feature.soundcontrol.SoundControlSettings;
import io.github.brener_fregulia.qolunified.feature.soundcontrol.SoundControlTexts;
import io.github.brener_fregulia.qolunified.feature.soundcontrol.SoundRule;
import io.github.brener_fregulia.qolunified.feature.soundcontrol.SoundRules;
import io.github.brener_fregulia.qolunified.mixin.SoundManagerAccessor;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only client game test for Sound Control (sound-control-v1.md §11, §12.1), in the disposable
 * {@code build/run-gametest-soundcontrol} directory. Gain math is checked through the engine's own volume
 * calculation and always runs; assertions that need an audio device (play results, history, OpenAL channel gains)
 * are reported as SKIPPED when the engine has none, never as passed.
 */
final class SoundControlGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");
    private static final String ID = SoundControlSettings.ID;
    private static final String VILLAGER = "minecraft:entity.villager.ambient";
    private static final String VILLAGER_HURT = "minecraft:entity.villager.hurt";
    private static final String CHICKEN = "minecraft:entity.chicken.ambient";
    private static final String COW = "minecraft:entity.cow.ambient";
    private static final String PIG = "minecraft:entity.pig.ambient";
    private static final String PORTAL = "minecraft:block.portal.ambient";
    private static final String TNT_PRIMED = "minecraft:entity.tnt.primed";
    private static final String CREEPER_PRIMED = "minecraft:entity.creeper.primed";
    private static final String EXPLODE = "minecraft:entity.generic.explode";
    private static final int AL_GAIN = 0x100A;

    private final ClientGameTestContext ctx;
    private final List<String> passed = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();
    private boolean audio;

    SoundControlGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    // ---- Scenario "soundcontrol" ----

    void run() {
        on(core -> check(core.lifecycle().state(ID) == FeatureState.ACTIVE, "enabled by default"));
        on(core -> check(rules(core).isEmpty(), "no rules on a new installation"));
        audio = ctx.computeOnClient(mc -> SoundControlHooks.audioAvailable());
        LOGGER.info("Sound Control game test: audio device {}", audio ? "available" : "NOT available");

        commit(Map.of(VILLAGER, new SoundRule(100, true), CHICKEN, new SoundRule(25, false),
                COW, new SoundRule(50, false)));
        factors();
        playResults();
        history();
        loopingRuntimeChange();
        cancelWithoutApplying();
        screen();
        resourceReload();
        tntAndCreeper();
        // Left committed for the restart scenario: configured 25%, then muted.
        commit(Map.of(CHICKEN, new SoundRule(25, true), VILLAGER, new SoundRule(100, true)));
        check(ctx.computeOnClient(mc -> SoundControlHooks.publishedFactor(Identifier.parse(CHICKEN))) == 0f,
                "muted chicken");
        report();
    }

    /** SC-AC-2/3: the hooked gain equals vanilla's gain times 0, 0.25, 0.5 and 1; unrelated events are untouched. */
    private void factors() {
        for (var expected : List.of(Map.entry(VILLAGER, 0f), Map.entry(CHICKEN, 0.25f), Map.entry(COW, 0.5f),
                Map.entry(PIG, 1f), Map.entry(VILLAGER_HURT, 1f))) {
            ctx.runOnClient(mc -> {
                SoundInstance sound = sound(mc, expected.getKey(), SoundSource.NEUTRAL, false);
                float vanilla = vanillaVolume(mc, sound);
                float hooked = hookedVolume(mc, sound);
                check(Math.abs(hooked - vanilla * expected.getValue()) < 1e-6f,
                        expected.getKey() + ": " + hooked + " = " + vanilla + " x " + expected.getValue());
                check(vanilla > 0f, "vanilla volume is audible for " + expected.getKey());
            });
        }
        pass("factors 0/25/50/100% and unrelated events (engine volume math)");
    }

    /** SC-AC-1: a muted event is not started; an unrelated one is. */
    private void playResults() {
        if (!audio) {
            skip("play results (no audio device)");
            return;
        }
        ctx.runOnClient(mc -> {
            var muted = mc.getSoundManager().play(sound(mc, VILLAGER, SoundSource.NEUTRAL, false));
            var other = mc.getSoundManager().play(sound(mc, VILLAGER_HURT, SoundSource.NEUTRAL, false));
            check(muted == SoundEngine.PlayResult.NOT_STARTED, "muted villager ambient not started: " + muted);
            check(other == SoundEngine.PlayResult.STARTED, "villager hurt started: " + other);
        });
        pass("muted event not started, unrelated event started");
    }

    /** SC-AC-6/17: history holds muted and unmuted events, most recent first, bounded. */
    private void history() {
        if (!audio) {
            skip("recent history capture (no audio device: vanilla notifies no listener)");
            return;
        }
        ctx.runOnClient(mc -> mc.getSoundManager().play(sound(mc, PIG, SoundSource.NEUTRAL, false)));
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            var ids = SoundControlHooks.recent().stream().map(e -> e.id().toString()).toList();
            check(ids.contains(VILLAGER), "muted event is in the history: " + ids);
            check(ids.indexOf(PIG) < ids.indexOf(VILLAGER), "most recent first: " + ids);
            check(ids.size() <= SoundControlSettings.RECENT_CAPACITY, "bounded");
            var pig = SoundControlHooks.recent().stream().filter(e -> e.id().toString().equals(PIG)).findFirst()
                    .orElseThrow();
            check(pig.category() == SoundSource.NEUTRAL && pig.count() >= 1, "category and count");
        });
        pass("recent history captures muted events, most recent first");
    }

    /** SC-AC-9/11: rule changes update the gain of a playing looping sound; disabling restores vanilla. */
    private void loopingRuntimeChange() {
        if (!audio) {
            skip("looping runtime change (no audio device)");
            return;
        }
        SoundInstance loop = ctx.computeOnClient(mc -> {
            SoundInstance sound = new SimpleSoundInstance(Identifier.parse(PORTAL), SoundSource.BLOCKS, 1f, 1f,
                    RandomSource.create(1), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            check(mc.getSoundManager().play(sound) == SoundEngine.PlayResult.STARTED, "loop started");
            return sound;
        });
        ctx.waitTicks(5);
        float full = channelGain(loop);
        check(full > 0f, "loop audible: " + full);
        commit(merged(PORTAL, new SoundRule(50, false)));
        float half = channelGain(loop);
        check(Math.abs(half - full * 0.5f) < 1e-3f, "loop at 50%: " + half + " of " + full);
        commit(merged(PORTAL, new SoundRule(50, true)));
        float muted = channelGain(loop);
        check(muted == 0f, "loop muted: " + muted);
        check(ctx.computeOnClient(mc -> mc.getSoundManager().isActive(loop)), "muted loop keeps playing silently");
        setEnabled(false);
        float restored = channelGain(loop);
        check(Math.abs(restored - full) < 1e-3f, "disabling restores vanilla gain: " + restored);
        setEnabled(true);
        check(channelGain(loop) == 0f, "enabling restores the committed (muted) rule");
        commit(without(PORTAL));
        check(Math.abs(channelGain(loop) - full) < 1e-3f, "reset restores vanilla gain");
        ctx.runOnClient(mc -> mc.getSoundManager().stop(loop));
        pass("playing loop follows 50%, mute, disable, enable and reset");
    }

    /** SC-AC-11 / M0 TX-2: an uncommitted draft changes nothing. */
    private void cancelWithoutApplying() {
        ctx.runOnClient(mc -> {
            var draft = new SettingsDraft(QolUnifiedClient.core());
            draft.setValue(ID, SoundControlSettings.RULES, SoundRules.write(Map.of(PIG, new SoundRule(0, true))));
        });
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> SoundControlHooks.publishedFactor(Identifier.parse(PIG))) == 1f,
                "draft without Done has no effect");
        pass("cancel without applying");
    }

    /** The screen from vanilla Music & Sound Options at GUI scales 1–4; Cancel discards, Done saves and applies. */
    private void screen() {
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.setScreen(() -> new SoundOptionsScreen(new TitleScreen(), Minecraft.getInstance().options));
        ctx.waitTicks(3);
        openFromOptions("01-sound-options-button");
        check(screenTitleKey().equals(SoundControlTexts.TITLE), "opened from Music & Sound Options");
        for (int scale = 1; scale <= 4; scale++) {
            setScale(scale);
            shot("02-recent-scale-" + scale);
        }
        setScale(2);
        click(SoundControlTexts.TAB_RULES);
        shot("03-rules");
        click(SoundControlTexts.TAB_ALL);
        shot("04-all-hint");
        ctx.getInput().typeChars("villager");
        ctx.waitTicks(2);
        shot("05-all-villager");
        check(ctx.computeOnClient(mc -> widget(mc.gui.screen(), SoundControlTexts.UNMUTE).isPresent()),
                "the search lists the muted villager");
        // The query is kept across tabs; clear it before working on the Rules tab.
        for (int i = 0; i < "villager".length(); i++) {
            ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_BACKSPACE));
        }
        ctx.waitTicks(2);
        // Cancel: toggling mute on the first row and leaving changes nothing.
        click(SoundControlTexts.TAB_RULES);
        click(SoundControlTexts.MUTE);
        click(UiKeys.CANCEL);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof SoundOptionsScreen), "Cancel returns");
        on(core -> check(rules(core).get(CHICKEN).equals(new SoundRule(25, false)), "Cancel saved nothing"));
        // Done: the same toggle is saved and applied (rules are sorted by name, so the first row is the chicken).
        openFromOptions(null);
        click(SoundControlTexts.TAB_RULES);
        click(SoundControlTexts.MUTE);
        shot("06-rules-muted");
        click(UiKeys.DONE);
        ctx.waitTicks(2);
        on(core -> check(rules(core).get(CHICKEN).equals(new SoundRule(25, true)), "Done saved the mute"));
        check(ctx.computeOnClient(mc -> SoundControlHooks.publishedFactor(Identifier.parse(CHICKEN))) == 0f,
                "Done applied the mute");
        // Narrow window: 854x480 (scale 2 is the largest vanilla allows there).
        ctx.getInput().resizeWindow(854, 480);
        openFromOptions(null);
        setScale(2);
        shot("07-narrow-854x480");
        click(SoundControlTexts.TAB_RULES);
        click(SoundControlTexts.RESET_ALL);
        shot("08-reset-all-confirm");
        pressKey(InputConstants.KEY_ESCAPE);
        click(UiKeys.CANCEL);
        ctx.setScreen(TitleScreen::new);
        ctx.waitForScreen(TitleScreen.class);
        // Restore the rules the rest of the test expects.
        commit(Map.of(VILLAGER, new SoundRule(100, true), CHICKEN, new SoundRule(25, false),
                COW, new SoundRule(50, false)));
        pass("screen at scales 1-4 and 854x480; Cancel discards; Done saves and applies");
    }

    /** SC-AC-10: rules still apply after a resource reload. */
    private void resourceReload() {
        CompletableFuture<Void> reload = ctx.computeOnClient(Minecraft::reloadResourcePacks);
        ctx.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 1200);
        ctx.waitTicks(5);
        factors();
        if (audio) {
            ctx.runOnClient(mc -> check(mc.getSoundManager().play(sound(mc, VILLAGER, SoundSource.NEUTRAL, false))
                    == SoundEngine.PlayResult.NOT_STARTED, "still muted after reload"));
        }
        pass("rules apply after resource reload");
    }

    /** SC-AC-18: the TNT fuse is its own event; TNT and Creeper explosions share one. */
    private void tntAndCreeper() {
        var heard = new LinkedHashMap<String, Float>();
        SoundEventListener probe = (sound, events, range) ->
                heard.putIfAbsent(sound.getIdentifier().toString(), SoundControlHooks.factor(sound));
        commit(Map.of(TNT_PRIMED, new SoundRule(100, true)));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("difficulty easy");
            ctx.runOnClient(mc -> mc.getSoundManager().addListener(probe));
            // TNT eight blocks away, primed by a redstone block: fuse sound, then the explosion.
            server.runCommand("execute at @p run setblock ~8 ~ ~ tnt");
            server.runCommand("execute at @p run setblock ~8 ~1 ~ redstone_block");
            ctx.waitTicks(100);
            ctx.runOnClient(mc -> {
                if (audio) {
                    check(heard.containsKey(TNT_PRIMED) && heard.get(TNT_PRIMED) == 0f, "TNT fuse muted: " + heard);
                    check(heard.containsKey(EXPLODE) && heard.get(EXPLODE) == 1f, "TNT explosion audible: " + heard);
                }
            });
            commit(Map.of(EXPLODE, new SoundRule(100, true)));
            heard.clear();
            server.runCommand("execute at @p run summon creeper ~ ~ ~-8 {ignited:1b,Fuse:20s,NoAI:1b}");
            ctx.waitTicks(60);
            ctx.runOnClient(mc -> {
                if (audio) {
                    check(heard.get(CREEPER_PRIMED) != null && heard.get(CREEPER_PRIMED) == 1f,
                            "Creeper hiss is not the TNT fuse: " + heard);
                    check(heard.get(EXPLODE) != null && heard.get(EXPLODE) == 0f,
                            "muting the explosion event also mutes the Creeper explosion: " + heard);
                }
                mc.getSoundManager().removeListener(probe);
            });
            shot("09-after-explosions");
        }
        ctx.waitForScreen(TitleScreen.class);
        if (audio) {
            pass("TNT fuse muted alone; TNT and Creeper explosions share one event");
        } else {
            skip("TNT/Creeper sound assertions (no audio device)");
        }
    }

    // ---- Scenario "soundcontrolrestart" ----

    void restart() {
        on(core -> {
            check(core.lifecycle().state(ID) == FeatureState.ACTIVE, "still enabled");
            check(rules(core).equals(Map.of(CHICKEN, new SoundRule(25, true), VILLAGER, new SoundRule(100, true))),
                    "rules restored: " + rules(core));
        });
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> SoundControlHooks.publishedFactor(Identifier.parse(CHICKEN))) == 0f,
                "muted after restart");
        Path config = FabricLoader.getInstance().getConfigDir().resolve("qol_unified").resolve("config.json");
        String json = read(config);
        check(json.contains("\"volume\": 25") && json.contains("\"muted\": true"), "volume and mute stored separately");
        commit(Map.of(CHICKEN, new SoundRule(25, false), VILLAGER, new SoundRule(100, true)));
        check(ctx.computeOnClient(mc -> SoundControlHooks.publishedFactor(Identifier.parse(CHICKEN))) == 0.25f,
                "unmute returns to 25% after restart");
        pass("restart restores rules; unmute returns to the configured 25%");
        report();
    }

    // ---- Helpers ----

    private static Map<String, SoundRule> rules(CoreRuntime core) {
        return SoundRules.read(core.choices().get(ID).settings().get(SoundControlSettings.RULES));
    }

    private Map<String, SoundRule> merged(String id, SoundRule rule) {
        var map = new LinkedHashMap<>(ctx.computeOnClient(mc -> rules(QolUnifiedClient.core())));
        map.put(id, rule);
        return map;
    }

    private Map<String, SoundRule> without(String id) {
        var map = new LinkedHashMap<>(ctx.computeOnClient(mc -> rules(QolUnifiedClient.core())));
        map.remove(id);
        return map;
    }

    /** Saves and applies like the settings screen's Done, then waits for the next tick's publication. */
    private void commit(Map<String, SoundRule> rules) {
        on(core -> {
            FeatureChoice choice = core.choices().get(ID);
            var result = core.saveAndApply(Map.of(ID, choice.withSettings(
                    choice.settings().with(SoundControlSettings.RULES, SoundRules.write(rules)))));
            check(result.save().persisted(), "saved: " + result.save());
        });
        ctx.waitTicks(2);
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of(ID, core.choices().get(ID).withEnabled(enabled))));
        ctx.waitTicks(2);
    }

    private static SoundInstance sound(Minecraft mc, String id, SoundSource source, boolean looping) {
        SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), source, 1f, 1f, RandomSource.create(7),
                looping, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        sound.getOrResolve(mc.getSoundManager());
        return sound;
    }

    private static SoundEngine engine(Minecraft mc) {
        return ((SoundManagerAccessor) mc.getSoundManager()).qolUnified$soundEngine();
    }

    /** {@code calculateVolume(float, SoundSource)}: vanilla's math, not hooked. */
    private static float vanillaVolume(Minecraft mc, SoundInstance sound) {
        try {
            Method m = SoundEngine.class.getDeclaredMethod("calculateVolume", float.class, SoundSource.class);
            m.setAccessible(true);
            return (float) m.invoke(engine(mc), sound.getVolume(), sound.getSource());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** {@code calculateVolume(SoundInstance)}: the hooked method used by ticking and refreshes. */
    private static float hookedVolume(Minecraft mc, SoundInstance sound) {
        try {
            Method m = SoundEngine.class.getDeclaredMethod("calculateVolume", SoundInstance.class);
            m.setAccessible(true);
            return (float) m.invoke(engine(mc), sound);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Reads AL_GAIN of the instance's OpenAL source on the sound thread. */
    private float channelGain(SoundInstance sound) {
        CompletableFuture<Float> gain = new CompletableFuture<>();
        ctx.runOnClient(mc -> {
            try {
                Field channels = SoundEngine.class.getDeclaredField("instanceToChannel");
                channels.setAccessible(true);
                Object handle = ((Map<?, ?>) channels.get(engine(mc))).get(sound);
                check(handle != null, "sound has a channel");
                Method execute = handle.getClass().getMethod("execute", Consumer.class);
                Consumer<Object> read = channel -> {
                    try {
                        Field source = channel.getClass().getDeclaredField("source");
                        source.setAccessible(true);
                        Method get = Class.forName("org.lwjgl.openal.AL10").getMethod("alGetSourcef", int.class,
                                int.class);
                        gain.complete((float) get.invoke(null, source.getInt(channel), AL_GAIN));
                    } catch (ReflectiveOperationException e) {
                        gain.completeExceptionally(e);
                    }
                };
                execute.invoke(handle, read);
            } catch (ReflectiveOperationException e) {
                gain.completeExceptionally(e);
            }
        });
        try {
            return gain.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("channel gain", e);
        }
    }

    /** The button is the last row of vanilla's options list; scroll to it like a player would, then click it. */
    private void openFromOptions(String screenshot) {
        ctx.runOnClient(mc -> visit(mc.gui.screen(), w -> {
            if (w instanceof net.minecraft.client.gui.components.OptionsList list) {
                list.setScrollAmount(list.maxScrollAmount());
            }
        }));
        ctx.waitTicks(3);
        if (screenshot != null) {
            shot(screenshot);
        }
        click(SoundControlTexts.OPEN_FROM_OPTIONS);
    }

    private void setScale(int scale) {
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        ctx.waitTicks(3);
    }

    private String screenTitleKey() {
        return ctx.computeOnClient(mc -> mc.gui.screen() != null
                && mc.gui.screen().getTitle().getContents() instanceof TranslatableContents t ? t.getKey() : "");
    }

    private void click(String key) {
        double[] position = ctx.computeOnClient(mc -> widget(mc.gui.screen(), key).map(w -> {
            int scale = mc.getWindow().getGuiScale();
            return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
        })).orElseThrow(() -> new AssertionError("No active widget " + key));
        ctx.getInput().setCursorPos(position[0], position[1]);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    private void pressKey(int key) {
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(key));
        ctx.waitTicks(3);
    }

    /** The first visible, active widget labelled with {@code key}, list rows included (top to bottom). */
    private static Optional<AbstractWidget> widget(Screen screen, String key) {
        var found = new ArrayList<AbstractWidget>();
        visit(screen, w -> {
            if (w.visible && w.isActive() && w.getMessage().getContents() instanceof TranslatableContents t
                    && t.getKey().equals(key) && w.getY() >= 0 && w.getY() < screen.height) {
                found.add(w);
            }
        });
        return found.stream().findFirst();
    }

    private static void visit(GuiEventListener listener, Consumer<AbstractWidget> action) {
        if (listener instanceof AbstractWidget widget) {
            action.accept(widget);
        }
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                visit(child, action);
            }
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private void shot(String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTicks(2);
        ctx.takeScreenshot("soundcontrol-" + name);
    }

    private void on(Consumer<CoreRuntime> assertion) {
        ctx.runOnClient(mc -> assertion.accept(QolUnifiedClient.core()));
    }

    private void pass(String what) {
        passed.add(what);
        LOGGER.info("Sound Control game test PASSED: {}", what);
    }

    private void skip(String what) {
        skipped.add(what);
        LOGGER.warn("Sound Control game test SKIPPED: {}", what);
    }

    private void report() {
        LOGGER.info("Sound Control game test summary: {} passed, {} skipped {}", passed.size(), skipped.size(),
                skipped);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
