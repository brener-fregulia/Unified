package unified.qol.ui;

/**
 * A screen that edits a settings draft (sound-control-v1.md §16). Features with a live preview use it to tell whether
 * the draft is still being edited: once no screen of that draft is shown, the transaction has ended (Done or Cancel)
 * and the preview must end too.
 */
public interface DraftScreen {
    SettingsDraft draft();
}
