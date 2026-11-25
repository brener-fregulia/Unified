package io.github.brener_fregulia.qolunified.feature.shulkerpreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.core.compat.ModPresence;
import io.github.brener_fregulia.qolunified.core.config.ConfigurationService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShulkerPreviewFeatureTest {
    @TempDir
    Path dir;

    private final AtomicInteger installs = new AtomicInteger();

    @AfterEach
    void deactivate() {
        new ShulkerPreviewFeature(() -> { }).disable();
    }

    private CoreRuntime start(ModPresence installed) {
        FeatureRegistry registry = FeatureRegistry.builder()
                .register(new ShulkerPreviewFeature(installs::incrementAndGet)).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry), installed,
                Thread.currentThread());
    }

    @Test
    void descriptorMatchesTheApprovedSpecification() {
        var d = ShulkerPreviewSettings.DESCRIPTOR;
        assertEquals("shulker_preview", d.id());
        assertEquals(FeatureCategory.INVENTORY, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        var defaults = new LinkedHashMap<String, Object>();
        for (SettingDescriptor s : d.settings()) {
            assertEquals(ApplyMode.LIVE, s.applyMode(), s.key());
            defaults.put(s.key(), s.defaultValue());
        }
        assertEquals(List.of("slot_icon", "compact_preview", "full_preview_on_shift", "slot_icon_source"),
                List.copyOf(defaults.keySet()));
        assertEquals(Map.of("slot_icon", true, "compact_preview", true, "full_preview_on_shift", true,
                "slot_icon_source", "first_occupied"), defaults);
        assertEquals(List.of("first_occupied", "predominant"),
                ((SettingDescriptor.EnumSetting) d.setting("slot_icon_source").orElseThrow()).values());
        assertEquals(List.of("shulkerboxtooltip", "simpleshulkerpreview"), d.conflicts().stream().map(ConflictDescriptor::modId).toList());
    }

    @Test
    void enabledByDefaultAndTogglesLive() {
        var core = start(mod -> false);
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("shulker_preview"));
        assertTrue(ShulkerPreviewFeature.activeSettings().getBoolean("compact_preview"));

        var choice = core.choices().get("shulker_preview");
        core.saveAndApply(Map.of("shulker_preview", choice.withSettings(choice.settings().with("compact_preview", false))));
        assertFalse(ShulkerPreviewFeature.activeSettings().getBoolean("compact_preview"));
        assertFalse(core.lifecycle().pendingRestart("shulker_preview"));

        core.saveAndApply(Map.of("shulker_preview", core.choices().get("shulker_preview").withEnabled(false)));
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("shulker_preview"));
        assertNull(ShulkerPreviewFeature.activeSettings());

        core.saveAndApply(Map.of("shulker_preview", core.choices().get("shulker_preview").withEnabled(true)));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("shulker_preview"));
        assertEquals(1, installs.get(), "the tooltip hook is registered once");
    }

    @Test
    void existingConfigurationsGetTheFirstOccupiedDefaultAndKeepTheirSettings() throws Exception {
        // A V1 configuration, written before slot_icon_source existed.
        java.nio.file.Files.writeString(dir.resolve("config.json"), """
                {"schemaVersion":1,"features":{"shulker_preview":{"enabled":true,
                 "settings":{"slot_icon":false,"compact_preview":false,"full_preview_on_shift":true}}}}""");
        var core = start(mod -> false);
        var settings = ShulkerPreviewFeature.activeSettings();
        assertEquals("first_occupied", settings.getEnum("slot_icon_source"));
        assertFalse(settings.getBoolean("slot_icon"), "existing value kept");
        assertFalse(settings.getBoolean("compact_preview"), "existing value kept");

        var choice = core.choices().get("shulker_preview");
        core.saveAndApply(Map.of("shulker_preview",
                choice.withSettings(choice.settings().with("slot_icon_source", "predominant"))));
        assertEquals("predominant", ShulkerPreviewFeature.activeSettings().getEnum("slot_icon_source"), "LIVE");
        assertFalse(core.lifecycle().pendingRestart("shulker_preview"));
        core.saveAndApply(Map.of("shulker_preview", core.choices().get("shulker_preview")
                .withSettings(core.choices().get("shulker_preview").settings().with("slot_icon_source",
                        "first_occupied"))));
        assertEquals("first_occupied", ShulkerPreviewFeature.activeSettings().getEnum("slot_icon_source"));
    }

    @Test
    void shulkerModsDisableDisableByDefaultAndAnExplicitEnableWins() {
        for (String mod : List.of("shulkerboxtooltip", "simpleshulkerpreview")) {
            deactivate();
            ModPresence present = mod::equals;
            Path config = dir.resolve(mod);
            FeatureRegistry registry = FeatureRegistry.builder().register(new ShulkerPreviewFeature(() -> { })).build();
            var core = CoreRuntime.start(registry, new ConfigurationService(config.resolve("config.json"), registry),
                    present, Thread.currentThread());
            assertEquals(FeatureState.INACTIVE, core.lifecycle().state("shulker_preview"), mod);
            assertTrue(core.compatibility("shulker_preview").defaultSuppressed(), mod);
            assertNull(ShulkerPreviewFeature.activeSettings());

            core.saveAndApply(Map.of("shulker_preview", core.choices().get("shulker_preview").withEnabled(true)));
            assertEquals(FeatureState.ACTIVE, core.lifecycle().state("shulker_preview"), mod);

            deactivate();
            var restarted = CoreRuntime.start(registry,
                    new ConfigurationService(config.resolve("config.json"), registry), present, Thread.currentThread());
            assertEquals(FeatureState.ACTIVE, restarted.lifecycle().state("shulker_preview"), mod + ": choice kept");
        }
    }
}
