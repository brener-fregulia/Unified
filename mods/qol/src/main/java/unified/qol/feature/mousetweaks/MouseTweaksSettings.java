package unified.qol.feature.mousetweaks;

import unified.qol.core.ApplyMode;
import unified.qol.core.ConflictDescriptor;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingDescriptor.BooleanSetting;
import unified.qol.core.SettingDescriptor.EnumSetting;
import java.util.List;

/** Inventory Mouse Tweaks descriptor and setting keys (mouse-tweaks-v1.md §0, §8). Free of Minecraft types. */
public final class MouseTweaksSettings {
    public static final String ID = "mouse_tweaks";
    /** Fabric mod ids, read from the published 26.3 JARs' fabric.mod.json (mouse-tweaks-v1.md §2.1). */
    public static final String MOUSE_TWEAKS = "mousetweaks";
    public static final String MOUSE_WHEELIE = "mousewheelie";
    public static final String ITEM_SCROLLER = "itemscroller";

    public static final String RMB_DRAG = "rmb_drag";
    public static final String LMB_COLLECT = "lmb_collect";
    public static final String SHIFT_DRAG = "shift_drag";
    public static final String WHEEL_TRANSFER = "wheel_transfer";
    public static final String WHEEL_DIRECTION = "wheel_direction";
    public static final String WHEEL_SEARCH_ORDER = "wheel_search_order";

    public static final String NORMAL = "normal";
    public static final String INVERTED = "inverted";
    public static final String LAST_TO_FIRST = "last_to_first";
    public static final String FIRST_TO_LAST = "first_to_last";

    private static final String PREFIX = "qol_unified.feature.mouse_tweaks.";

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.INVENTORY, true,
            ApplyMode.LIVE,
            List.of(
                    new BooleanSetting(RMB_DRAG, true, ApplyMode.LIVE),
                    new BooleanSetting(LMB_COLLECT, true, ApplyMode.LIVE),
                    new BooleanSetting(SHIFT_DRAG, true, ApplyMode.LIVE),
                    new BooleanSetting(WHEEL_TRANSFER, true, ApplyMode.LIVE),
                    new EnumSetting(WHEEL_DIRECTION, NORMAL, List.of(NORMAL, INVERTED), ApplyMode.LIVE),
                    new EnumSetting(WHEEL_SEARCH_ORDER, LAST_TO_FIRST, List.of(LAST_TO_FIRST, FIRST_TO_LAST),
                            ApplyMode.LIVE)),
            List.of(new ConflictDescriptor(MOUSE_TWEAKS, PREFIX + "conflict.mousetweaks"),
                    new ConflictDescriptor(MOUSE_WHEELIE, PREFIX + "conflict.mousewheelie"),
                    new ConflictDescriptor(ITEM_SCROLLER, PREFIX + "conflict.itemscroller")));

    private MouseTweaksSettings() {
    }
}
