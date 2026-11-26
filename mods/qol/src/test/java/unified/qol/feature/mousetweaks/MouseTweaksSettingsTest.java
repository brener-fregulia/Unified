package unified.qol.feature.mousetweaks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.ApplyMode;
import unified.qol.core.ConflictDescriptor;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.FeatureState;
import unified.qol.core.SettingDescriptor;
import unified.qol.core.config.ConfigurationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Descriptor, LIVE settings, conflicts and translations (mouse-tweaks-v1.md §0, §7, §8). */
class MouseTweaksSettingsTest {
    @TempDir
    Path dir;

    @AfterEach
    void deactivate() {
        new MouseTweaksFeature().disable();
    }

    private CoreRuntime start(Set<String> installed) {
        FeatureRegistry registry = FeatureRegistry.builder().register(new MouseTweaksFeature()).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry),
                installed::contains, Thread.currentThread());
    }

    @Test
    void descriptorMatchesTheApprovedSpecification() {
        var d = MouseTweaksSettings.DESCRIPTOR;
        assertEquals("mouse_tweaks", d.id());
        assertEquals(FeatureCategory.INVENTORY, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        var defaults = new LinkedHashMap<String, Object>();
        for (SettingDescriptor s : d.settings()) {
            assertEquals(ApplyMode.LIVE, s.applyMode(), s.key());
            defaults.put(s.key(), s.defaultValue());
        }
        assertEquals(List.of("rmb_drag", "lmb_collect", "shift_drag", "wheel_transfer", "wheel_direction",
                "wheel_search_order"), List.copyOf(defaults.keySet()));
        assertEquals(List.of(true, true, true, true, "normal", "last_to_first"), List.copyOf(defaults.values()));
        assertEquals(List.of("mousetweaks", "mousewheelie", "itemscroller"),
                d.conflicts().stream().map(ConflictDescriptor::modId).toList());
    }

    @Test
    void settingsApplyLiveAndOffMeansNoGestures() {
        var core = start(Set.of());
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("mouse_tweaks"));
        assertTrue(MouseTweaksFeature.config().rmbDrag());
        var choice = core.choices().get("mouse_tweaks");
        core.saveAndApply(Map.of("mouse_tweaks", choice.withSettings(choice.settings().with("rmb_drag", false))));
        assertEquals(false, MouseTweaksFeature.config().rmbDrag(), "next event sees the new value");
        core.saveAndApply(Map.of("mouse_tweaks", core.choices().get("mouse_tweaks").withEnabled(false)));
        assertEquals(null, MouseTweaksFeature.config(), "disabled: every gesture is vanilla");
    }

    @Test
    void overlappingModsSuppressTheFeatureByDefault() {
        for (String mod : List.of("mousetweaks", "mousewheelie", "itemscroller")) {
            var core = start(Set.of(mod));
            assertEquals(FeatureState.INACTIVE, core.lifecycle().state("mouse_tweaks"), mod);
            assertTrue(core.compatibility("mouse_tweaks").defaultSuppressed(), mod);
        }
        var core = start(Set.of("inventoryprofilesnext", "moremousetweaks"));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("mouse_tweaks"), "not declared conflicts");
    }

    @Test
    void everyTextExistsInEnglishAndPortuguese() throws Exception {
        var gson = new com.google.gson.Gson();
        Map<String, Set<String>> keys = new HashMap<>();
        for (String lang : List.of("en_us", "pt_br")) {
            try (var in = getClass().getResourceAsStream("/assets/qol_unified/lang/" + lang + ".json")) {
                var json = gson.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                        com.google.gson.JsonObject.class);
                keys.put(lang, json.keySet().stream().filter(k -> k.contains(".mouse_tweaks."))
                        .collect(Collectors.toSet()));
            }
        }
        assertEquals(keys.get("en_us"), keys.get("pt_br"));
        var d = MouseTweaksSettings.DESCRIPTOR;
        assertTrue(keys.get("en_us").containsAll(List.of(d.nameKey(), d.descriptionKey(),
                "qol_unified.feature.mouse_tweaks.note")));
        for (SettingDescriptor s : d.settings()) {
            assertTrue(keys.get("en_us").contains(s.nameKey("mouse_tweaks")), s.key());
            if (s instanceof SettingDescriptor.EnumSetting e) {
                e.values().forEach(v -> assertTrue(keys.get("en_us").contains(s.nameKey("mouse_tweaks") + "." + v)));
            }
        }
        d.conflicts().forEach(c -> assertTrue(keys.get("en_us").contains(c.reasonKey()), c.reasonKey()));
    }
}
