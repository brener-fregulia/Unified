package io.github.brener_fregulia.qolunified.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft.CommitOutcome.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsDraftTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config.json");
    }

    @Test
    void editsStayInDraftAndCancelDiscards() throws Exception {
        var f = UiFixtures.feature("feat", FeatureCategory.INVENTORY);
        var core = UiFixtures.start(dir, Set.of(), f);
        var draft = new SettingsDraft(core);
        draft.setEnabled("feat", false);
        draft.setValue("feat", "mode", "b");
        assertTrue(draft.changed());
        // Cancel = dropping the draft: nothing saved, nothing applied.
        assertFalse(Files.exists(file()));
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("feat"));
        assertEquals("a", core.lifecycle().effectiveSettings("feat").getEnum("mode"));
        var fresh = new SettingsDraft(core);
        assertTrue(fresh.choice("feat").enabled());
        assertFalse(fresh.changed());
    }

    @Test
    void unchangedDoneDoesNothing() {
        var f = UiFixtures.feature("feat", FeatureCategory.INVENTORY);
        var core = UiFixtures.start(dir, Set.of(), f);
        assertEquals(Kind.NO_CHANGE, new SettingsDraft(core).commit().kind());
        assertFalse(Files.exists(file()));
        assertEquals(List.of("enable"), f.calls);
    }

    @Test
    void invalidIntegerAndEnumInputBlockCommit() {
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        assertEquals(Optional.of(UiKeys.ERROR_NOT_A_NUMBER), draft.setIntegerText("feat", "size", "abc"));
        assertTrue(draft.hasInvalidInput("feat", "size"));
        assertEquals(Optional.of("abc"), draft.invalidInput("feat", "size"));
        assertEquals(Kind.INVALID, draft.commit().kind());
        assertEquals(Optional.of(UiKeys.ERROR_OUT_OF_RANGE), draft.setIntegerText("feat", "size", "11"));
        assertEquals(Kind.INVALID, draft.commit().kind());
        assertEquals(Optional.empty(), draft.setIntegerText("feat", "size", " 7 "));
        assertEquals(Optional.of(UiKeys.ERROR_INVALID_VALUE), draft.setValue("feat", "mode", "zzz"));
        assertEquals(Kind.INVALID, draft.commit().kind());
        assertEquals(Optional.empty(), draft.setValue("feat", "mode", "b"));
        assertTrue(draft.valid());
        assertFalse(Files.exists(file()), "nothing saved while invalid");
        assertEquals(Kind.APPLIED, draft.commit().kind());
    }

    @Test
    void liveSettingAppliesAndRestartRequiredSettingIsPending() throws Exception {
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        draft.setValue("feat", "mode", "b");
        draft.setIntegerText("feat", "size", "9");
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertEquals("b", core.lifecycle().effectiveSettings("feat").getEnum("mode"));
        assertEquals(5, core.lifecycle().effectiveSettings("feat").getInt("size"));
        assertTrue(draft.restartRequired("feat"));
        assertTrue(FeatureStatus.keys(core, "feat").contains(UiKeys.STATUS_PENDING_RESTART));
        assertTrue(Files.readString(file()).contains("\"size\": 9"));
    }

    @Test
    void explicitConflictOverrideIsPersistedAndExplicitDisableKept() throws Exception {
        var hud = UiFixtures.conflicting("hud", "other_hud");
        var core = UiFixtures.start(dir, Set.of("other_hud"), hud);
        var draft = new SettingsDraft(core);
        assertFalse(draft.choice("hud").enabled());
        assertEquals(List.of(UiKeys.STATUS_CONFLICT_DEFAULT), FeatureStatus.keys(core, "hud"),
                "the conflict default replaces the plain Inactive status");
        draft.setEnabled("hud", true);
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("hud"));
        assertTrue(FeatureStatus.keys(core, "hud").contains(UiKeys.STATUS_CONFLICT_OVERRIDE));
        assertTrue(Files.readString(file()).contains("\"enabled\": true"));

        var again = new SettingsDraft(core);
        again.setEnabled("hud", false);
        assertEquals(Kind.APPLIED, again.commit().kind());
        assertTrue(Files.readString(file()).contains("\"enabled\": false"));
        assertFalse(new SettingsDraft(core).choice("hud").enabled());
    }

    @Test
    void readOnlyConfigurationPreventsSaving() throws Exception {
        Files.writeString(file(), "{\"schemaVersion\":99}");
        String before = Files.readString(file());
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        assertFalse(draft.editable());
        assertFalse(draft.canToggle("feat"));
        assertThrows(IllegalStateException.class, () -> draft.setEnabled("feat", false));
        assertEquals(Kind.READ_ONLY, draft.commit().kind());
        assertEquals(before, Files.readString(file()));
    }

    @Test
    void saveFailureKeepsDraftAndAppliesNothing() throws Exception {
        var f = UiFixtures.feature("feat", FeatureCategory.INVENTORY);
        var core = UiFixtures.start(dir, Set.of(), f);
        var draft = new SettingsDraft(core);
        draft.setEnabled("feat", false);
        Files.createDirectory(dir.resolve("config.json.tmp")); // makes the temporary-file write fail
        assertEquals(Kind.SAVE_FAILED, draft.commit().kind());
        assertFalse(draft.choice("feat").enabled(), "draft kept");
        assertTrue(draft.changed());
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("feat"));
        assertEquals(List.of("enable"), f.calls);
    }

    @Test
    void runtimeFailureIsReportedSeparatelyAndNeverRetried() throws Exception {
        var f = UiFixtures.feature("feat", FeatureCategory.INVENTORY);
        f.disableFailures = 2;
        var core = UiFixtures.start(dir, Set.of(), f);
        var draft = new SettingsDraft(core);
        draft.setEnabled("feat", false);
        var outcome = draft.commit();
        assertEquals(Kind.APPLIED_WITH_FAILURES, outcome.kind());
        assertEquals("feat", outcome.failures().getFirst().featureId());
        assertTrue(Files.readString(file()).contains("\"enabled\": false"), "saved despite runtime failure");
        assertEquals(FeatureState.UNKNOWN, core.lifecycle().state("feat"));
        int calls = f.calls.size();

        // Acknowledging the result (pressing Done again) does not re-apply.
        assertEquals(Kind.NO_CHANGE, draft.commit().kind());
        assertEquals(calls, f.calls.size());

        // UNKNOWN offers only Retry disable.
        assertFalse(draft.canToggle("feat"));
        assertThrows(IllegalStateException.class, () -> draft.setEnabled("feat", true));
        assertTrue(FeatureStatus.offersRetryDisable(core, "feat"));
        assertTrue(FeatureStatus.keys(core, "feat").contains(UiKeys.STATUS_UNKNOWN));
        assertTrue(draft.retryDisable("feat").failed());
        assertFalse(draft.retryDisable("feat").failed());
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("feat"));
        assertFalse(FeatureStatus.offersRetryDisable(core, "feat"));
    }

    @Test
    void failedEnableWithCleanupIsShownAsEnableFailure() {
        var f = UiFixtures.feature("feat", FeatureCategory.INVENTORY);
        f.enableFailures = 1;
        var core = UiFixtures.start(dir, Set.of(), f);
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("feat"));
        assertEquals(List.of(UiKeys.STATUS_ENABLE_FAILED), FeatureStatus.keys(core, "feat"));
        assertTrue(new SettingsDraft(core).canToggle("feat"));
    }
}
