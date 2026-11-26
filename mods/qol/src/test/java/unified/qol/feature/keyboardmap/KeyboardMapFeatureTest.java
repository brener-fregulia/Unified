package unified.qol.feature.keyboardmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.Features;
import unified.qol.core.ApplyMode;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.FeatureState;
import unified.qol.core.SettingDescriptor;
import unified.qol.core.config.ConfigurationService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyboardMapFeatureTest {
    @TempDir
    Path dir;

    private final AtomicInteger installs = new AtomicInteger();

    @AfterEach
    void deactivate() {
        new KeyboardMapFeature(() -> { }).disable();
    }

    private CoreRuntime start() {
        FeatureRegistry registry = FeatureRegistry.builder()
                .register(new KeyboardMapFeature(installs::incrementAndGet)).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry),
                mod -> false, Thread.currentThread());
    }

    @Test
    void descriptorMatchesTheApproval() {
        var d = KeyboardMapSettings.DESCRIPTOR;
        assertEquals("keyboard_map", d.id());
        assertEquals(FeatureCategory.CONTROLS, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        assertTrue(d.conflicts().isEmpty());
        var defaults = new LinkedHashMap<String, Object>();
        for (SettingDescriptor s : d.settings()) {
            assertEquals(ApplyMode.LIVE, s.applyMode(), s.key());
            defaults.put(s.key(), s.defaultValue());
        }
        assertEquals(Map.of("layout", "us_ansi", "show_unbound_keys", true, "contextual_labels", true,
                "default_filter", "all"), defaults);
        assertEquals(List.of("us_ansi", "abnt2"),
                ((SettingDescriptor.EnumSetting) d.setting("layout").orElseThrow()).values());
        assertEquals(List.of("all", "overlaps", "assigned", "unbound"),
                ((SettingDescriptor.EnumSetting) d.setting("default_filter").orElseThrow()).values());
        assertTrue(Features.createRegistry().features().stream()
                .anyMatch(f -> f.descriptor().id().equals("keyboard_map")));
    }

    @Test
    void togglesLiveAndInstallsEntryPointsOnce() {
        var core = start();
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("keyboard_map"));
        assertEquals("us_ansi", KeyboardMapFeature.activeSettings().getEnum("layout"));
        core.saveAndApply(Map.of("keyboard_map", core.choices().get("keyboard_map").withEnabled(false)));
        assertNull(KeyboardMapFeature.activeSettings(), "disabled: entry points open nothing");
        core.saveAndApply(Map.of("keyboard_map", core.choices().get("keyboard_map").withEnabled(true)));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("keyboard_map"));
        assertEquals(1, installs.get());
    }

    @Test
    void layoutAndFilterPersistAcrossRestarts() {
        var core = start();
        var choice = core.choices().get("keyboard_map");
        core.saveAndApply(Map.of("keyboard_map", choice.withSettings(choice.settings().with("layout", "abnt2")
                .with("default_filter", "overlaps").with("contextual_labels", false))));
        assertEquals("abnt2", KeyboardMapFeature.activeSettings().getEnum("layout"), "LIVE");
        new KeyboardMapFeature(() -> { }).disable();

        var restarted = start();
        var settings = restarted.choices().get("keyboard_map").settings();
        assertEquals("abnt2", settings.getEnum("layout"));
        assertEquals("overlaps", settings.getEnum("default_filter"));
        assertEquals(false, settings.getBoolean("contextual_labels"));
    }
}
