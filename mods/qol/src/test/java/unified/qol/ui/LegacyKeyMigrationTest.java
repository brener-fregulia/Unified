package unified.qol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import unified.qol.ui.screen.LegacyKeyMigration;

/** The one-time move of the former QoL settings key (unified-settings-integration.md §20.6). */
class LegacyKeyMigrationTest {
    @Test
    void readsACustomLegacyBinding() {
        assertEquals(Optional.of("key.keyboard.keypad.9"), LegacyKeyMigration.legacyBinding(List.of(
                "version:4790", "key_key.qol_unified.open_settings:key.keyboard.keypad.9", "key_key.jump:key.keyboard.space")));
    }

    @Test
    void ignoresAnUnboundOrMissingLegacyKey() {
        assertEquals(Optional.empty(), LegacyKeyMigration.legacyBinding(List.of(
                "key_key.qol_unified.open_settings:key.keyboard.unknown")));
        assertEquals(Optional.empty(), LegacyKeyMigration.legacyBinding(List.of("key_key.jump:key.keyboard.space")));
        assertEquals(Optional.empty(), LegacyKeyMigration.legacyBinding(List.of()));
    }

    @Test
    void neverOverwritesACustomizedSharedKey() {
        assertTrue(LegacyKeyMigration.shouldMigrate(Optional.of("key.keyboard.k"), true));
        assertFalse(LegacyKeyMigration.shouldMigrate(Optional.of("key.keyboard.k"), false));
        assertFalse(LegacyKeyMigration.shouldMigrate(Optional.empty(), true));
    }
}
