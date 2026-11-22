package io.github.brener_fregulia.qolunified.feature.foodhud;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.BooleanSetting;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.EnumSetting;
import java.util.List;

/** Food HUD descriptor and setting keys (food-hud-v1.md §6). Free of Minecraft types. */
public final class FoodHudSettings {
    public static final String ID = "food_hud";
    public static final String APPLESKIN = "appleskin";

    public static final String SATURATION_OVERLAY = "saturation_overlay";
    public static final String FOOD_PREVIEW = "food_preview";
    public static final String HEALTH_PREVIEW = "health_preview";
    public static final String OFFHAND_PREVIEW = "offhand_preview";
    public static final String TOOLTIP = "tooltip";
    public static final String MATCH_ANIMATIONS = "match_animations";
    public static final String DEBUG_INFO = "debug_info";

    public static final String TOOLTIP_OFF = "off";
    public static final String TOOLTIP_SHIFT = "shift";
    public static final String TOOLTIP_ALWAYS = "always";

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.HUD_INFORMATION,
            true, ApplyMode.LIVE,
            List.of(
                    new BooleanSetting(SATURATION_OVERLAY, true, ApplyMode.LIVE),
                    new BooleanSetting(FOOD_PREVIEW, true, ApplyMode.LIVE),
                    new BooleanSetting(HEALTH_PREVIEW, true, ApplyMode.LIVE),
                    new BooleanSetting(OFFHAND_PREVIEW, true, ApplyMode.LIVE),
                    new EnumSetting(TOOLTIP, TOOLTIP_ALWAYS, List.of(TOOLTIP_OFF, TOOLTIP_SHIFT, TOOLTIP_ALWAYS),
                            ApplyMode.LIVE),
                    new BooleanSetting(MATCH_ANIMATIONS, true, ApplyMode.LIVE),
                    new BooleanSetting(DEBUG_INFO, true, ApplyMode.LIVE)),
            List.of(new ConflictDescriptor(APPLESKIN, "qol_unified.feature.food_hud.conflict.appleskin")));

    private FoodHudSettings() {
    }
}
