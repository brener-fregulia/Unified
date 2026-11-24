package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.core.compat.ModPresence;
import io.github.brener_fregulia.qolunified.core.config.ConfigFileSystem;
import io.github.brener_fregulia.qolunified.core.config.ConfigurationService;
import io.github.brener_fregulia.qolunified.core.config.FeatureChoice;
import io.github.brener_fregulia.qolunified.core.config.Migrations;
import io.github.brener_fregulia.qolunified.core.config.SaveResult;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lifecycle, LIVE application, persistence of mute memory and conflict policy (sound-control-v1.md §0, §5, §8). */
class SoundControlFeatureTest {
    @TempDir
    Path dir;

    /** Records what the feature publishes instead of touching Minecraft. */
    private static final class FakeRuntime implements SoundControlFeature.Runtime {
        int installs;
        boolean history;
        final List<Map<String, SoundRule>> published = new ArrayList<>();

        @Override
        public void install(SoundControlFeature feature) {
            installs++;
        }

        @Override
        public void publish(Map<String, SoundRule> rules) {
            published.add(rules);
        }

        @Override
        public void startHistory() {
            history = true;
        }

        @Override
        public void stopHistory() {
            history = false;
        }

        Map<String, SoundRule> last() {
            return published.getLast();
        }
    }

    private final FakeRuntime runtime = new FakeRuntime();
    private SoundControlFeature feature;

    @AfterEach
    void deactivate() {
        new SoundControlFeature(new FakeRuntime()).disable();
    }

    private CoreRuntime start(ModPresence installed, ConfigFileSystem fs) {
        feature = new SoundControlFeature(runtime);
        FeatureRegistry registry = FeatureRegistry.builder().register(feature).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry,
                Migrations.production(), fs, Clock.systemUTC()), installed, Thread.currentThread());
    }

    private CoreRuntime start() {
        return start(mod -> false, new ConfigFileSystem());
    }

    private static CoreRuntime.CommitResult setRules(CoreRuntime core, Map<String, SoundRule> rules) {
        FeatureChoice choice = core.choices().get(SoundControlSettings.ID);
        return core.saveAndApply(Map.of(SoundControlSettings.ID, choice.withSettings(
                choice.settings().with(SoundControlSettings.RULES, SoundRules.write(rules)))));
    }

    @Test
    void descriptorMatchesTheApprovedSpecification() {
        var d = SoundControlSettings.DESCRIPTOR;
        assertEquals("sound_control", d.id());
        assertEquals(FeatureCategory.AUDIO, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        assertEquals(List.of("event_rules"), d.settings().stream().map(s -> s.key()).toList());
        assertEquals(ApplyMode.LIVE, d.settings().getFirst().applyMode());
        assertEquals(4096, SoundControlSettings.RULES_SETTING.maxEntries());
        assertEquals(128, SoundControlSettings.RECENT_CAPACITY);
        assertEquals(List.of("extremesoundmuffler", "soundcontroller", "decibel"),
                d.conflicts().stream().map(ConflictDescriptor::modId).toList());
    }

    @Test
    void enablePublishesCommittedRulesAndSettingsApplyLiveAfterConfirmation() {
        var core = start();
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("sound_control"));
        assertEquals(1, runtime.installs);
        assertTrue(runtime.history, "history listens during ordinary play");
        assertEquals(Map.of(), runtime.last(), "no rules: vanilla");

        int before = runtime.published.size();
        feature.sync();
        assertEquals(before, runtime.published.size(), "nothing republished without a change");

        var rules = Map.of("minecraft:entity.villager.ambient", new SoundRule(25, true));
        assertEquals(SaveResult.SAVED, setRules(core, rules).save());
        assertEquals(before, runtime.published.size(), "published on the next client tick, not mid-commit");
        feature.sync();
        assertEquals(rules, runtime.last());
        assertEquals(0f, runtime.last().get("minecraft:entity.villager.ambient").factor());
    }

    @Test
    void disableRestoresVanillaAndEnableRestoresCommittedRules() {
        var core = start();
        var rules = Map.of("minecraft:entity.generic.explode", new SoundRule(50, false));
        setRules(core, rules);
        feature.sync();

        core.saveAndApply(Map.of("sound_control", core.choices().get("sound_control").withEnabled(false)));
        assertEquals(Map.of(), runtime.last(), "disabling publishes no rules");
        assertTrue(!runtime.history);
        assertNull(SoundControlFeature.activeSettings());
        int count = runtime.published.size();
        feature.sync();
        assertEquals(count, runtime.published.size(), "no work while disabled");

        core.saveAndApply(Map.of("sound_control", core.choices().get("sound_control").withEnabled(true)));
        assertEquals(rules, runtime.last());
        assertEquals(1, runtime.installs, "hooks are installed once");
    }

    @Test
    void failedSaveAppliesNothing() {
        var fs = new ConfigFileSystem() {
            @Override
            public void write(Path path, byte[] content) throws IOException {
                throw new IOException("disk full");
            }
        };
        var core = start(mod -> false, fs);
        int count = runtime.published.size();
        assertEquals(SaveResult.FAILED, setRules(core, Map.of("a:b", new SoundRule(10, false))).save());
        feature.sync();
        assertEquals(count, runtime.published.size(), "rules are not applied when persistence fails");
        assertEquals(Map.of(), SoundRules.read(core.choices().get("sound_control").settings()
                .get(SoundControlSettings.RULES)));
    }

    @Test
    void muteKeepsTheConfiguredVolumeAcrossRestart() {
        var core = start();
        setRules(core, Map.of("minecraft:entity.chicken.ambient", new SoundRule(25, true)));
        deactivate();

        var restarted = start();
        var rules = SoundRules.read(restarted.choices().get("sound_control").settings()
                .get(SoundControlSettings.RULES));
        SoundRule rule = rules.get("minecraft:entity.chicken.ambient");
        assertEquals(new SoundRule(25, true), rule);
        assertEquals(0f, rule.factor());
        assertEquals(0.25f, rule.withMuted(false).factor(), "unmute returns to 25%");
        assertEquals(rules, runtime.last(), "restored rules are published at startup");
    }

    @Test
    void conflictingModsDisableByDefaultAndAnExplicitEnableWins() {
        for (String mod : List.of("extremesoundmuffler", "soundcontroller", "decibel")) {
            deactivate();
            Path config = dir.resolve(mod).resolve("config.json");
            var local = new FakeRuntime();
            FeatureRegistry registry = FeatureRegistry.builder().register(new SoundControlFeature(local)).build();
            ModPresence present = mod::equals;
            var core = CoreRuntime.start(registry, new ConfigurationService(config, registry), present,
                    Thread.currentThread());
            assertEquals(FeatureState.INACTIVE, core.lifecycle().state("sound_control"), mod);
            assertTrue(core.compatibility("sound_control").defaultSuppressed(), mod);
            assertEquals(0, local.installs, mod + ": nothing installed while suppressed");

            core.saveAndApply(Map.of("sound_control", core.choices().get("sound_control").withEnabled(true)));
            assertEquals(FeatureState.ACTIVE, core.lifecycle().state("sound_control"), mod);
        }
    }
}
