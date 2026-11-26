package unified.spike.parenta;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.LoggerFactory;
import unified.settings.spike.SettingsProvider;
import unified.settings.spike.UnifiedSettings;

/** Stands in for Unified: QoL. Compiled against contract 1.0. */
public final class ParentA implements ClientModInitializer, SettingsProvider {
    @Override
    public void onInitializeClient() {
        LoggerFactory.getLogger("spike_parent_a").info("[spike] parent A sees unified_settings {}",
                UnifiedSettings.version());
    }

    @Override
    public String describe() {
        return "Parent A (compiled against contract 1.0)";
    }
}
