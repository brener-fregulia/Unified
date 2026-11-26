package unified.qol.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureState;
import unified.qol.ui.SettingsDraft.CommitOutcome.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The workspace's auto-apply contract (settings-ux-v1.md §18): one long-lived draft, committed after every valid change
 * with the M0 save-before-apply transaction, rebased after each success, and reverted after a failed save.
 */
class SettingsAutoApplyTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config.json");
    }

    @Test
    void eachSwitchCommitSavesThenAppliesAndTheDraftKeepsWorking() throws Exception {
        var f = UiFixtures.feature("feat", FeatureCategory.INVENTORY);
        var core = UiFixtures.start(dir, Set.of(), f);
        var draft = new SettingsDraft(core);
        draft.setEnabled("feat", false);
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("feat"));
        assertTrue(Files.readString(file()).contains("\"enabled\": false"));
        assertFalse(draft.changed(), "rebased on the saved state");
        // The same draft commits the next change too.
        draft.setEnabled("feat", true);
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("feat"));
        assertEquals(List.of("enable", "disable", "enable"), f.calls);
    }

    @Test
    void booleansAndEnumsCommitAtOnceAndUnchangedValuesWriteNothing() throws Exception {
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        draft.setValue("feat", "live_flag", true);
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertTrue(core.lifecycle().effectiveSettings("feat").getBoolean("live_flag"));
        draft.setValue("feat", "mode", "b");
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertEquals("b", core.lifecycle().effectiveSettings("feat").getEnum("mode"));
        byte[] saved = Files.readAllBytes(file());
        draft.setValue("feat", "mode", "b"); // the same value again
        assertEquals(Kind.NO_CHANGE, draft.commit().kind());
        assertArrayEquals(saved, Files.readAllBytes(file()), "no write for an unchanged value");
    }

    @Test
    void validationComesBeforePersistence() throws Exception {
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        draft.setIntegerText("feat", "size", "abc");
        assertEquals(Kind.INVALID, draft.commit().kind());
        assertFalse(Files.exists(file()), "invalid input is never saved");
        draft.revert();
        assertTrue(draft.valid(), "revert clears the invalid input");
    }

    @Test
    void aFailedSaveAppliesNothingAndRevertRestoresTheStoredState() throws Exception {
        var f = UiFixtures.feature("feat", FeatureCategory.INVENTORY);
        var core = UiFixtures.start(dir, Set.of(), f);
        var draft = new SettingsDraft(core);
        Path blocker = dir.resolve("config.json.tmp");
        Files.createDirectory(blocker);
        Files.writeString(blocker.resolve("keep"), "x"); // the temporary file can be neither written nor removed
        draft.setEnabled("feat", false);
        assertEquals(Kind.SAVE_FAILED, draft.commit().kind());
        assertEquals(FeatureState.ACTIVE, core.lifecycle().state("feat"), "save-before-apply: nothing applied");
        assertFalse(Files.exists(file()));
        assertEquals(Kind.SAVE_FAILED, draft.commit().kind(), "retry while still blocked");
        Files.delete(blocker.resolve("keep"));
        Files.delete(blocker);
        assertEquals(Kind.APPLIED, draft.commit().kind(), "retry succeeds");
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("feat"));

        Files.createDirectory(blocker);
        Files.writeString(blocker.resolve("keep"), "x");
        draft.setEnabled("feat", true);
        assertEquals(Kind.SAVE_FAILED, draft.commit().kind());
        draft.revert();
        assertFalse(draft.choice("feat").enabled(), "undo shows the stored value");
        assertFalse(draft.changed());
        assertEquals(FeatureState.INACTIVE, core.lifecycle().state("feat"));
    }

    @Test
    void readOnlyConfigurationsNeverCommit() throws Exception {
        Files.writeString(file(), "{\"schemaVersion\":99}");
        String before = Files.readString(file());
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        assertFalse(draft.editable());
        assertEquals(Kind.READ_ONLY, draft.commit().kind());
        assertEquals(before, Files.readString(file()));
    }

    @Test
    void restartRequiredChangesAreSavedButNotActive() throws Exception {
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        draft.setIntegerText("feat", "size", "9");
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertEquals(5, core.lifecycle().effectiveSettings("feat").getInt("size"), "not claimed active");
        assertTrue(draft.restartRequired("feat"));
        assertTrue(Files.readString(file()).contains("\"size\": 9"));
    }

    @Test
    void conflictOverridesAndUnknownFeaturesKeepTheirRules() throws Exception {
        var hud = UiFixtures.conflicting("hud", "other_hud");
        var broken = UiFixtures.feature("broken", FeatureCategory.INVENTORY);
        broken.disableFailures = 1;
        var core = UiFixtures.start(dir, Set.of("other_hud"), hud, broken);
        var draft = new SettingsDraft(core);
        draft.setEnabled("hud", true); // explicit choice over the conflict default
        assertEquals(Kind.APPLIED, draft.commit().kind());
        assertTrue(core.compatibility("hud").playerOverride());
        draft.setEnabled("broken", false);
        assertEquals(Kind.APPLIED_WITH_FAILURES, draft.commit().kind());
        assertEquals(FeatureState.UNKNOWN, core.lifecycle().state("broken"));
        assertFalse(draft.canToggle("broken"), "UNKNOWN: only Retry disable");
        assertEquals(FeatureState.INACTIVE, draft.retryDisable("broken").state());
    }

    @Test
    void closingNeedsNoCommit() throws Exception {
        var core = UiFixtures.start(dir, Set.of(), UiFixtures.feature("feat", FeatureCategory.INVENTORY));
        var draft = new SettingsDraft(core);
        draft.setValue("feat", "mode", "b");
        draft.commit();
        byte[] saved = Files.readAllBytes(file());
        // The workspace closes without touching the draft; a fresh session sees the saved value.
        assertEquals("b", new SettingsDraft(core).choice("feat").settings().getEnum("mode"));
        assertArrayEquals(saved, Files.readAllBytes(file()));
    }
}
