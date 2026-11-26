package unified.qol.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.config.ConfigurationService;
import unified.qol.core.config.LoadStatus;
import unified.qol.core.config.SaveResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreRuntimeTest {
    @TempDir
    Path dir;

    private CoreRuntime start(TestFeature... features) {
        return start(modId -> false, features);
    }

    private CoreRuntime start(unified.qol.core.compat.ModPresence installed,
            TestFeature... features) {
        var builder = FeatureRegistry.builder();
        for (TestFeature f : features) {
            builder.register(f);
        }
        FeatureRegistry registry = builder.build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry), installed,
                Thread.currentThread());
    }

    @Test
    void startupUsesLoadedConfiguration() throws Exception {
        Files.writeString(dir.resolve("config.json"), """
                {"schemaVersion":1,"features":{"feat":{"enabled":false,"settings":{"restart_size":8}}}}""");
        var f = TestFeature.live("feat");
        var core = start(f);
        assertEquals(LoadStatus.LOADED, core.configurationStatus());
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("feat"));
        assertEquals(List.of(), f.calls);
        assertEquals(8, core.lifecycle().effectiveSettings("feat").getInt("restart_size"));
    }

    @Test
    void saveThenApplySeparatesLiveAndRestartRequiredSettings() throws Exception {
        var f = TestFeature.live("feat");
        var core = start(f);
        var choice = core.choices().get("feat");
        var draft = choice.withSettings(choice.settings().with("live_flag", true).with("restart_size", 9));
        var result = core.saveAndApply(Map.of("feat", draft));
        assertEquals(SaveResult.SAVED, result.save());
        assertTrue(core.lifecycle().effectiveSettings("feat").getBoolean("live_flag"));
        assertEquals(5, core.lifecycle().effectiveSettings("feat").getInt("restart_size"));
        assertEquals(9, core.lifecycle().desiredSettings("feat").getInt("restart_size"));
        assertTrue(core.lifecycle().pendingRestart("feat"));
        assertTrue(Files.readString(dir.resolve("config.json")).contains("\"restart_size\": 9"));
    }

    @Test
    void nothingIsAppliedWhenSaveDoesNotPersist() throws Exception {
        Files.writeString(dir.resolve("config.json"), "{\"schemaVersion\":42}");
        var f = TestFeature.live("feat");
        var core = start(f);
        assertTrue(core.configurationReadOnly());
        var result = core.saveAndApply(Map.of("feat", core.choices().get("feat").withEnabled(false)));
        assertEquals(SaveResult.READ_ONLY, result.save());
        assertTrue(result.report().outcomes().isEmpty());
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("feat"));
        assertEquals(List.of("enable"), f.calls);
    }

    @Test
    void runtimeFailureDoesNotUndoSave() throws Exception {
        var f = TestFeature.live("feat").failDisable(1);
        var core = start(f);
        var result = core.saveAndApply(Map.of("feat", core.choices().get("feat").withEnabled(false)));
        assertEquals(SaveResult.SAVED, result.save());
        assertTrue(result.report().hasFailures());
        assertEquals(FeatureState.UNKNOWN, core.lifecycle().state("feat"));
        assertTrue(Files.readString(dir.resolve("config.json")).contains("\"enabled\": false"));
        assertFalse(core.choices().get("feat").enabled());
        // Committing the same draft again (closing the results) does not retry.
        int calls = f.calls.size();
        assertEquals(SaveResult.NO_CHANGE,
                core.saveAndApply(Map.of("feat", core.choices().get("feat"))).save());
        assertEquals(calls, f.calls.size());
        assertEquals(TransitionOutcome.Kind.DISABLED, core.retryDisable("feat").kind());
    }

    @Test
    void operationsOffTheClientThreadAreRejected() {
        var core = start(TestFeature.live("feat"));
        var future = CompletableFuture.runAsync(() -> core.saveAndApply(Map.of()));
        var error = assertThrows(ExecutionException.class, future::get);
        assertTrue(error.getCause() instanceof IllegalStateException);
        assertThrows(IllegalStateException.class, () -> CoreRuntime.start(FeatureRegistry.builder().build(),
                new ConfigurationService(dir.resolve("x.json"), FeatureRegistry.builder().build()), modId -> false, new Thread()));
    }

    private static TestFeature conflicting(String id, String modId) {
        var base = TestFeature.descriptor(id, ApplyMode.LIVE, true);
        return new TestFeature(new FeatureDescriptor(id, base.category(), true, ApplyMode.LIVE, base.settings(),
                List.of(new ConflictDescriptor(modId, "qol_unified.conflict." + id))));
    }

    @Test
    void conflictDisablesByDefaultWithoutWritingConfiguration() throws Exception {
        var hud = conflicting("hud", "other_hud");
        var plain = TestFeature.live("plain");
        var core = start(Set.of("other_hud")::contains, hud, plain);
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("hud"));
        assertEquals(List.of(), hud.calls);
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("plain"), "unrelated feature unaffected");
        assertFalse(Files.exists(dir.resolve("config.json")), "detection never writes");
        assertFalse(core.choices().get("hud").enabled());
        assertTrue(core.compatibility("hud").defaultSuppressed());
        assertEquals(1, core.compatibilityReport().all().size());

        // A draft started from the resolved choices does not persist the conflict default.
        var result = core.saveAndApply(core.choices());
        assertFalse(Files.readString(dir.resolve("config.json")).contains("enabled"));
        assertTrue(result.report().outcomes().isEmpty(), "no lifecycle calls");
    }

    @Test
    void explicitEnableStartsDespiteConflict() throws Exception {
        Files.writeString(dir.resolve("config.json"),
                "{\"schemaVersion\":1,\"features\":{\"hud\":{\"enabled\":true}}}");
        String before = Files.readString(dir.resolve("config.json"));
        var hud = conflicting("hud", "other_hud");
        var core = start(Set.of("other_hud")::contains, hud);
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("hud"));
        assertTrue(core.compatibility("hud").playerOverride());
        assertEquals(before, Files.readString(dir.resolve("config.json")));
    }

    @Test
    void explicitDisableStaysDisabledWithConflict() throws Exception {
        Files.writeString(dir.resolve("config.json"),
                "{\"schemaVersion\":1,\"features\":{\"hud\":{\"enabled\":false}}}");
        var core = start(Set.of("other_hud")::contains, conflicting("hud", "other_hud"));
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("hud"));
        assertFalse(core.compatibility("hud").playerOverride());
    }

    @Test
    void playerCanEnableConflictingFeatureThroughSaveAndApply() throws Exception {
        var hud = conflicting("hud", "other_hud");
        var core = start(Set.of("other_hud")::contains, hud);
        var result = core.saveAndApply(Map.of("hud", core.choices().get("hud").withEnabled(true)));
        assertEquals(SaveResult.SAVED, result.save());
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("hud"));
        assertTrue(core.compatibility("hud").playerOverride());
        assertTrue(Files.readString(dir.resolve("config.json")).contains("\"enabled\": true"));
    }
}
