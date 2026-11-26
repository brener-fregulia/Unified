package unified.qol.feature.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.ApplyMode;
import unified.qol.core.ConflictDescriptor;
import unified.qol.core.CoreRuntime;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.FeatureState;
import unified.qol.core.SettingDescriptor;
import unified.qol.core.config.ConfigurationService;
import unified.qol.ui.SettingsDraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Zoom descriptor, bounds rule and conflicts (zoom-v1.md §0, §10, §13). */
class ZoomSettingsTest {
    @TempDir
    Path dir;

    /** The real descriptor without Minecraft hooks. */
    private static final class Stub implements Feature {
        @Override
        public FeatureDescriptor descriptor() {
            return ZoomSettings.DESCRIPTOR;
        }

        @Override
        public void enable(FeatureContext context) {
        }

        @Override
        public void disable() {
        }
    }

    private CoreRuntime start(Set<String> installed) {
        FeatureRegistry registry = FeatureRegistry.builder().register(new Stub()).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry),
                installed::contains, Thread.currentThread());
    }

    @Test
    void descriptorMatchesTheApprovedSpecification() {
        var d = ZoomSettings.DESCRIPTOR;
        assertEquals("zoom", d.id());
        assertEquals(FeatureCategory.CONTROLS, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        var defaults = new LinkedHashMap<String, Object>();
        for (SettingDescriptor s : d.settings()) {
            assertEquals(ApplyMode.LIVE, s.applyMode(), s.key());
            defaults.put(s.key(), s.defaultValue());
        }
        assertEquals(List.of("zoom_mode", "initial_magnification", "min_magnification", "max_magnification",
                "scroll_zoom", "zoom_in_ms", "zoom_out_ms", "transition", "reduce_sensitivity"),
                List.copyOf(defaults.keySet()));
        assertEquals(List.of("hold", 4, 2, 14, true, 1000, 0, "smooth", true), List.copyOf(defaults.values()));
        assertEquals(List.of("zoomify", "ok_zoomer", "logical_zoom"),
                d.conflicts().stream().map(ConflictDescriptor::modId).toList());
    }

    @Test
    void contradictoryBoundsAreRejectedAtTheField() throws Exception {
        var core = start(Set.of());
        var draft = new SettingsDraft(core);
        assertEquals(ZoomSettings.ERROR_MIN, draft.ruleViolation("zoom", "min_magnification", 10)
                .orElseThrow().messageKey(), "minimum above the initial 4×");
        assertEquals(List.of(4), draft.ruleViolation("zoom", "min_magnification", 10).orElseThrow().args());
        assertEquals(ZoomSettings.ERROR_MAX, draft.ruleViolation("zoom", "max_magnification", 3)
                .orElseThrow().messageKey());
        assertTrue(draft.ruleViolation("zoom", "initial_magnification", 10).isEmpty(), "inside 2–14");
        // Through the draft itself: the contradiction is never part of what is saved.
        assertTrue(draft.setValue("zoom", "max_magnification", 2).isPresent());
        assertFalse(draft.valid());
        draft.revert();
        assertTrue(draft.valid());
        // A coherent path exists one field at a time: raise initial, then the minimum.
        assertTrue(draft.setValue("zoom", "initial_magnification", 8).isEmpty());
        assertTrue(draft.setValue("zoom", "min_magnification", 8).isEmpty());
        assertEquals(SettingsDraft.CommitOutcome.Kind.APPLIED, draft.commit().kind());
        assertEquals(8, core.lifecycle().effectiveSettings("zoom").getInt("min_magnification"));
        assertTrue(Files.readString(dir.resolve("config.json")).contains("\"min_magnification\": 8"));
    }

    @Test
    void aHandEditedContradictionFallsBackToDefaults() throws Exception {
        Files.writeString(dir.resolve("config.json"), """
                {"schemaVersion":1,"features":{"zoom":{"settings":
                {"min_magnification":10,"max_magnification":2,"zoom_in_ms":500}}}}""");
        var core = start(Set.of());
        var settings = core.lifecycle().effectiveSettings("zoom");
        assertEquals(2, settings.getInt("min_magnification"));
        assertEquals(14, settings.getInt("max_magnification"));
        assertEquals(1000, settings.getInt("zoom_in_ms"), "the whole feature's settings, not a mix");
    }

    @Test
    void everyZoomTextExistsInEnglishAndPortuguese() throws Exception {
        var gson = new com.google.gson.Gson();
        java.util.Map<String, java.util.Set<String>> keys = new java.util.HashMap<>();
        for (String lang : List.of("en_us", "pt_br")) {
            try (var in = getClass().getResourceAsStream("/assets/qol_unified/lang/" + lang + ".json")) {
                var json = gson.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                        com.google.gson.JsonObject.class);
                keys.put(lang, json.keySet().stream().filter(k -> k.contains(".zoom")).collect(
                        java.util.stream.Collectors.toSet()));
            }
        }
        assertEquals(keys.get("en_us"), keys.get("pt_br"));
        var d = ZoomSettings.DESCRIPTOR;
        assertTrue(keys.get("en_us").contains(d.nameKey()));
        assertTrue(keys.get("en_us").contains("qol_unified.key.zoom"));
        for (SettingDescriptor s : d.settings()) {
            assertTrue(keys.get("en_us").contains(s.nameKey("zoom")), s.key());
            if (s instanceof SettingDescriptor.EnumSetting e) {
                e.values().forEach(v -> assertTrue(keys.get("en_us").contains(s.nameKey("zoom") + "." + v), v));
            }
        }
        for (String error : List.of(ZoomSettings.ERROR_INITIAL, ZoomSettings.ERROR_MIN, ZoomSettings.ERROR_MAX)) {
            assertTrue(keys.get("en_us").contains(error), error);
        }
        d.conflicts().forEach(c -> assertTrue(keys.get("en_us").contains(c.reasonKey()), c.reasonKey()));
    }

    @Test
    void aZoomModSuppressesZoomUnlessThePlayerChoosesIt() {
        for (String mod : List.of("zoomify", "ok_zoomer", "logical_zoom")) {
            var core = start(Set.of(mod));
            assertEquals(FeatureState.INACTIVE, core.lifecycle().state("zoom"), mod);
            assertTrue(core.compatibility("zoom").defaultSuppressed(), mod);
        }
        var core = start(Set.of("sodium", "iris"));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("zoom"), "Sodium and Iris are not conflicts");
    }
}
