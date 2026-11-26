package io.github.brener_fregulia.qolunified.feature.targetinfo;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.BooleanSetting;
import java.util.List;

/** Target Info descriptor and setting keys (target-info-v1.md §0, §9). Free of Minecraft types. */
public final class TargetInfoSettings {
    public static final String ID = "target_info";
    /** Fabric mod ids, read from the published 26.3 JARs' fabric.mod.json (Jade 26.3.5+fabric, WTHIT 21.0.0). */
    public static final String JADE = "jade";
    public static final String WTHIT = "wthit";

    public static final String SHOW_BLOCKS = "show_blocks";
    public static final String SHOW_ENTITIES = "show_entities";
    public static final String SHOW_TOOLS = "show_tools";
    public static final String SHOW_MINING_PROGRESS = "show_mining_progress";
    public static final String SHOW_BLOCK_DETAILS = "show_block_details";
    public static final String SHOW_HEALTH = "show_health";
    public static final String SHOW_OWNER = "show_owner";
    public static final String SHOW_SOURCE = "show_source";

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.HUD_INFORMATION,
            true, ApplyMode.LIVE,
            List.of(
                    new BooleanSetting(SHOW_BLOCKS, true, ApplyMode.LIVE),
                    new BooleanSetting(SHOW_ENTITIES, true, ApplyMode.LIVE),
                    new BooleanSetting(SHOW_TOOLS, true, ApplyMode.LIVE),
                    new BooleanSetting(SHOW_MINING_PROGRESS, true, ApplyMode.LIVE),
                    new BooleanSetting(SHOW_BLOCK_DETAILS, true, ApplyMode.LIVE),
                    new BooleanSetting(SHOW_HEALTH, true, ApplyMode.LIVE),
                    new BooleanSetting(SHOW_OWNER, true, ApplyMode.LIVE),
                    new BooleanSetting(SHOW_SOURCE, true, ApplyMode.LIVE)),
            List.of(new ConflictDescriptor(JADE, "qol_unified.feature.target_info.conflict.jade"),
                    new ConflictDescriptor(WTHIT, "qol_unified.feature.target_info.conflict.wthit")));

    private TargetInfoSettings() {
    }
}
