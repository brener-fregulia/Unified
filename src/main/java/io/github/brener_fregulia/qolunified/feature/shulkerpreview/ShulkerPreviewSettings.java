package io.github.brener_fregulia.qolunified.feature.shulkerpreview;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.BooleanSetting;
import java.util.List;

/** Shulker Preview descriptor and setting keys (shulker-preview-v1.md §1.1, §7). Free of Minecraft types. */
public final class ShulkerPreviewSettings {
    public static final String ID = "shulker_preview";
    /** Fabric mod ids read from the published 26.3 JARs (Shulker Box Tooltip 5.4.3, Simple Shulker Preview 2.6.0). */
    public static final String SHULKER_BOX_TOOLTIP = "shulkerboxtooltip";
    public static final String SIMPLE_SHULKER_PREVIEW = "simpleshulkerpreview";

    public static final String SLOT_ICON = "slot_icon";
    public static final String COMPACT_PREVIEW = "compact_preview";
    public static final String FULL_PREVIEW_ON_SHIFT = "full_preview_on_shift";

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.INVENTORY,
            true, ApplyMode.LIVE,
            List.of(
                    new BooleanSetting(SLOT_ICON, true, ApplyMode.LIVE),
                    new BooleanSetting(COMPACT_PREVIEW, true, ApplyMode.LIVE),
                    new BooleanSetting(FULL_PREVIEW_ON_SHIFT, true, ApplyMode.LIVE)),
            List.of(new ConflictDescriptor(SHULKER_BOX_TOOLTIP,
                            "qol_unified.feature.shulker_preview.conflict.shulkerboxtooltip"),
                    new ConflictDescriptor(SIMPLE_SHULKER_PREVIEW,
                            "qol_unified.feature.shulker_preview.conflict.simpleshulkerpreview")));

    /** Which tooltip to show for a box with known contents (shulker-preview-v1.md §6, §7). */
    public enum Mode {
        VANILLA,
        COMPACT,
        FULL
    }

    private ShulkerPreviewSettings() {
    }

    public static Mode mode(boolean compact, boolean fullOnShift, boolean shiftDown) {
        if (fullOnShift && shiftDown) {
            return Mode.FULL;
        }
        return compact ? Mode.COMPACT : Mode.VANILLA;
    }
}
