package unified.qol.feature.foodhud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.ApplyMode;
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

class FoodHudFeatureTest {
    @TempDir
    Path dir;

    private final AtomicInteger installs = new AtomicInteger();

    @AfterEach
    void deactivate() {
        new FoodHudFeature(() -> { }).disable();
    }

    private CoreRuntime start(ModPresence installed) {
        FeatureRegistry registry = FeatureRegistry.builder()
                .register(new FoodHudFeature(installs::incrementAndGet)).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry), installed,
                Thread.currentThread());
    }

    @Test
    void descriptorMatchesTheApprovedSpecification() {
        var d = FoodHudSettings.DESCRIPTOR;
        assertEquals("food_hud", d.id());
        assertEquals(FeatureCategory.HUD_INFORMATION, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        var defaults = new LinkedHashMap<String, Object>();
        for (SettingDescriptor s : d.settings()) {
            assertEquals(ApplyMode.LIVE, s.applyMode(), s.key());
            defaults.put(s.key(), s.defaultValue());
        }
        assertEquals(Map.of("saturation_overlay", true, "food_preview", true, "health_preview", true,
                "offhand_preview", true, "tooltip", "always", "match_animations", true, "debug_info", true), defaults);
        assertEquals(List.of("off", "shift", "always"),
                ((SettingDescriptor.EnumSetting) d.setting("tooltip").orElseThrow()).values());
        assertEquals("appleskin", d.conflicts().getFirst().modId());
    }

    @Test
    void enabledByDefaultAndTogglesLive() {
        var core = start(mod -> false);
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("food_hud"));
        assertTrue(FoodHudFeature.activeSettings().getBoolean("food_preview"));

        var choice = core.choices().get("food_hud");
        core.saveAndApply(Map.of("food_hud", choice.withSettings(choice.settings().with("food_preview", false)
                .with("tooltip", "shift"))));
        assertFalse(FoodHudFeature.activeSettings().getBoolean("food_preview"));
        assertEquals("shift", FoodHudFeature.activeSettings().getEnum("tooltip"));
        assertFalse(core.lifecycle().pendingRestart("food_hud"));

        core.saveAndApply(Map.of("food_hud", core.choices().get("food_hud").withEnabled(false)));
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("food_hud"));
        assertNull(FoodHudFeature.activeSettings());

        core.saveAndApply(Map.of("food_hud", core.choices().get("food_hud").withEnabled(true)));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("food_hud"));
        assertEquals(1, installs.get(), "hooks are installed once");
    }

    @Test
    void appleSkinDisablesByDefaultAndAnExplicitEnableWins() {
        ModPresence appleSkin = "appleskin"::equals;
        var core = start(appleSkin);
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("food_hud"));
        assertNull(FoodHudFeature.activeSettings());
        assertEquals(0, installs.get());

        core.saveAndApply(Map.of("food_hud", core.choices().get("food_hud").withEnabled(true)));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("food_hud"));

        new FoodHudFeature(() -> { }).disable();
        var restarted = start(appleSkin);
        assertEquals(FeatureState.ACTIVE, restarted.lifecycle().state("food_hud"));
    }
}
