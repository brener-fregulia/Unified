package unified.qol.feature.targetinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.ApplyMode;
import unified.qol.core.ConflictDescriptor;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.FeatureState;
import unified.qol.core.SettingDescriptor;
import unified.qol.core.compat.ModPresence;
import unified.qol.core.config.ConfigurationService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetInfoFeatureTest {
    @TempDir
    Path dir;

    private final AtomicInteger installs = new AtomicInteger();

    @AfterEach
    void deactivate() {
        new TargetInfoFeature(() -> { }).disable();
    }

    private CoreRuntime start(ModPresence installed) {
        FeatureRegistry registry = FeatureRegistry.builder()
                .register(new TargetInfoFeature(installs::incrementAndGet)).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry), installed,
                Thread.currentThread());
    }

    @Test
    void descriptorMatchesTheApprovedSpecification() {
        var d = TargetInfoSettings.DESCRIPTOR;
        assertEquals("target_info", d.id());
        assertEquals(FeatureCategory.HUD_INFORMATION, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        var defaults = new LinkedHashMap<String, Object>();
        for (SettingDescriptor s : d.settings()) {
            assertEquals(ApplyMode.LIVE, s.applyMode(), s.key());
            defaults.put(s.key(), s.defaultValue());
        }
        assertEquals(List.of("show_blocks", "show_entities", "show_tools", "show_mining_progress",
                "show_block_details", "show_health", "show_owner", "show_source"), List.copyOf(defaults.keySet()));
        assertTrue(defaults.values().stream().allMatch(Boolean.TRUE::equals));
        assertEquals(List.of("jade", "wthit"), d.conflicts().stream().map(ConflictDescriptor::modId).toList());
    }

    @Test
    void enabledByDefaultAndTogglesLive() {
        var core = start(mod -> false);
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("target_info"));
        assertTrue(TargetInfoFeature.activeSettings().getBoolean("show_health"));

        var choice = core.choices().get("target_info");
        core.saveAndApply(Map.of("target_info", choice.withSettings(choice.settings().with("show_health", false))));
        assertFalse(TargetInfoFeature.activeSettings().getBoolean("show_health"));
        assertFalse(core.lifecycle().pendingRestart("target_info"));

        core.saveAndApply(Map.of("target_info", core.choices().get("target_info").withEnabled(false)));
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("target_info"));
        assertNull(TargetInfoFeature.activeSettings());

        core.saveAndApply(Map.of("target_info", core.choices().get("target_info").withEnabled(true)));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("target_info"));
        assertEquals(1, installs.get(), "the HUD element is registered once");
    }

    @Test
    void jadeOrWthitDisableByDefaultAndAnExplicitEnableWins() {
        for (String mod : List.of("jade", "wthit")) {
            deactivate();
            ModPresence present = mod::equals;
            Path config = dir.resolve(mod);
            FeatureRegistry registry = FeatureRegistry.builder().register(new TargetInfoFeature(() -> { })).build();
            var core = CoreRuntime.start(registry, new ConfigurationService(config.resolve("config.json"), registry),
                    present, Thread.currentThread());
            assertEquals(FeatureState.INACTIVE, core.lifecycle().state("target_info"), mod);
            assertTrue(core.compatibility("target_info").defaultSuppressed(), mod);
            assertNull(TargetInfoFeature.activeSettings());

            core.saveAndApply(Map.of("target_info", core.choices().get("target_info").withEnabled(true)));
            assertEquals(FeatureState.ACTIVE, core.lifecycle().state("target_info"), mod);

            deactivate();
            var restarted = CoreRuntime.start(registry,
                    new ConfigurationService(config.resolve("config.json"), registry), present, Thread.currentThread());
            assertEquals(FeatureState.ACTIVE, restarted.lifecycle().state("target_info"), mod + ": choice kept");
        }
    }
}
