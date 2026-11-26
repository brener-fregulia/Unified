package unified.qol.feature.keyboardmap;

import unified.qol.core.ApplyMode;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingDescriptor.BooleanSetting;
import unified.qol.core.SettingDescriptor.EnumSetting;
import java.util.Arrays;
import java.util.List;

/** Keyboard map descriptor and setting keys (keymap-v1.md §0, §10). Free of Minecraft types. */
public final class KeyboardMapSettings {
    public static final String ID = "keyboard_map";

    public static final String LAYOUT = "layout";
    public static final String SHOW_UNBOUND_KEYS = "show_unbound_keys";
    public static final String CONTEXTUAL_LABELS = "contextual_labels";
    public static final String DEFAULT_FILTER = "default_filter";

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.CONTROLS,
            true, ApplyMode.LIVE,
            List.of(
                    new EnumSetting(LAYOUT, KeyboardLayouts.US_ANSI,
                            List.of(KeyboardLayouts.US_ANSI, KeyboardLayouts.ABNT2), ApplyMode.LIVE),
                    new BooleanSetting(SHOW_UNBOUND_KEYS, true, ApplyMode.LIVE),
                    new BooleanSetting(CONTEXTUAL_LABELS, true, ApplyMode.LIVE),
                    new EnumSetting(DEFAULT_FILTER, BindingFilter.Visibility.ALL.id(),
                            Arrays.stream(BindingFilter.Visibility.values()).map(BindingFilter.Visibility::id).toList(),
                            ApplyMode.LIVE)),
            List.of());

    private KeyboardMapSettings() {
    }
}
