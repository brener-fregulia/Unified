package io.github.brener_fregulia.qolunified.feature.chatheads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.Features;
import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.core.compat.ModPresence;
import io.github.brener_fregulia.qolunified.core.config.ConfigurationService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatHeadsFeatureTest {
    @TempDir
    Path dir;

    private final AtomicInteger installs = new AtomicInteger();

    @AfterEach
    void deactivate() {
        new ChatHeadsFeature(() -> { }).disable();
    }

    private CoreRuntime start(ModPresence installed) {
        FeatureRegistry registry = FeatureRegistry.builder()
                .register(new ChatHeadsFeature(installs::incrementAndGet)).build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry), installed,
                Thread.currentThread());
    }

    @Test
    void descriptorMatchesTheApprovedSpecification() {
        var d = ChatHeadsSettings.DESCRIPTOR;
        assertEquals("chat_heads", d.id());
        assertEquals(FeatureCategory.CHAT_SOCIAL, d.category());
        assertTrue(d.defaultEnabled());
        assertEquals(ApplyMode.LIVE, d.toggleMode());
        assertEquals(1, d.settings().size());
        var position = (SettingDescriptor.EnumSetting) d.setting("position").orElseThrow();
        assertEquals("before_name", position.defaultValue());
        assertEquals(List.of("before_name", "before_line"), position.values());
        assertEquals(ApplyMode.LIVE, position.applyMode());
        assertEquals(List.of("chat_heads"), d.conflicts().stream().map(c -> c.modId()).toList());
    }

    @Test
    void registeredInProduction() {
        assertTrue(Features.createRegistry().features().stream()
                .anyMatch(f -> f.descriptor().id().equals("chat_heads")));
    }

    @Test
    void enabledByDefaultAndTogglesLive() {
        var core = start(mod -> false);
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("chat_heads"));
        assertEquals("before_name", ChatHeadsFeature.position());

        var choice = core.choices().get("chat_heads");
        core.saveAndApply(Map.of("chat_heads", choice.withSettings(choice.settings().with("position", "before_line"))));
        assertEquals("before_line", ChatHeadsFeature.position());
        assertEquals(false, core.lifecycle().pendingRestart("chat_heads"));

        core.saveAndApply(Map.of("chat_heads", core.choices().get("chat_heads").withEnabled(false)));
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("chat_heads"));
        assertNull(ChatHeadsFeature.position());

        core.saveAndApply(Map.of("chat_heads", core.choices().get("chat_heads").withEnabled(true)));
        assertEquals("before_line", ChatHeadsFeature.position(), "setting kept across toggles");
        assertEquals(1, installs.get(), "hooks are installed once");
    }

    @Test
    void originalChatHeadsDisablesByDefaultAndAnExplicitEnableWins() {
        ModPresence chatHeads = "chat_heads"::equals;
        var core = start(chatHeads);
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("chat_heads"));
        assertTrue(core.compatibility("chat_heads").defaultSuppressed());
        assertNull(ChatHeadsFeature.position());
        assertEquals(0, installs.get());

        core.saveAndApply(Map.of("chat_heads", core.choices().get("chat_heads").withEnabled(true)));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("chat_heads"));

        new ChatHeadsFeature(() -> { }).disable();
        assertEquals(FeatureState.ACTIVE, start(chatHeads).lifecycle().state("chat_heads"), "explicit choice persists");
    }
}
