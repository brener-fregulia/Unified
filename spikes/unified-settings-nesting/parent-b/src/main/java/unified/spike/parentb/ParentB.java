package unified.spike.parentb;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.LoggerFactory;
import unified.settings.spike.SettingsProvider;
import unified.settings.spike.UnifiedSettings;

/** Stands in for Unified: Optimizations. Compiled against contract 1.1 and overrides its new method. */
public final class ParentB implements ClientModInitializer, SettingsProvider {
    @Override
    public void onInitializeClient() {
        LoggerFactory.getLogger("spike_parent_b").info("[spike] parent B sees unified_settings {}",
                UnifiedSettings.version());
    }

    @Override
    public String describe() {
        return "Parent B (compiled against contract 1.1)";
    }

    @Override
    public int order() {
        return 20;
    }
}
