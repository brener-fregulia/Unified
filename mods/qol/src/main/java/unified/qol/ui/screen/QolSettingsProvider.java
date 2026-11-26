package unified.qol.ui.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import unified.qol.QolUnifiedClient;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.ui.UiKeys;
import unified.settings.api.ModuleOrder;
import unified.settings.api.SettingsCategory;
import unified.settings.api.UnifiedSettingsProvider;

/**
 * Unified: QoL's entry in Unified Settings (contract 1.0, docs/architecture/unified-settings-integration.md §19).
 * Opens the existing settings workspace unchanged; QoL keeps its own configuration and auto-apply.
 */
public final class QolSettingsProvider implements UnifiedSettingsProvider {
    @Override
    public String getModuleId() {
        return QolUnifiedClient.MOD_ID;
    }

    @Override
    public String getNameKey() {
        return "modmenu.nameTranslation.qol_unified";
    }

    @Override
    public int getOrder() {
        return ModuleOrder.QOL;
    }

    /** The categories that have features, in catalog order, with their feature counts. */
    @Override
    public List<SettingsCategory> categories() {
        Map<FeatureCategory, Integer> counts = new EnumMap<>(FeatureCategory.class);
        for (Feature feature : QolUnifiedClient.core().registry().features()) {
            counts.merge(feature.descriptor().category(), 1, Integer::sum);
        }
        List<SettingsCategory> categories = new ArrayList<>();
        counts.forEach((category, count) -> categories.add(new SettingsCategory(
                category.name().toLowerCase(Locale.ROOT), UiKeys.category(category), count)));
        return categories;
    }

    @Override
    public Screen openSettings(Screen parent) {
        return new SettingsScreen(parent, QolUnifiedClient.core());
    }
}
