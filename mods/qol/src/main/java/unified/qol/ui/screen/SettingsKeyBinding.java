package unified.qol.ui.screen;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import unified.qol.QolUnifiedClient;

/**
 * The "Unified: QoL" key-mapping category of the feature keys (Zoom, Keyboard Map). The settings workspace opens with
 * the shared "Open Unified Settings" key of Unified Settings; QoL's former settings key is migrated by
 * {@link LegacyKeyMigration}.
 */
public final class SettingsKeyBinding {
    private SettingsKeyBinding() {
    }

    private static KeyMapping.Category category;

    /** The "Unified: QoL" key-mapping category, shared by every Unified: QoL key mapping. */
    public static KeyMapping.Category category() {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(QolUnifiedClient.MOD_ID, "main"));
        }
        return category;
    }
}
