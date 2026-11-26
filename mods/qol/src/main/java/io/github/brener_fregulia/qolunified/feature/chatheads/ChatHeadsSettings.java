package io.github.brener_fregulia.qolunified.feature.chatheads;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.EnumSetting;
import java.util.List;

/** Chat Heads descriptor and setting keys (chat-heads-v1.md §7). Free of Minecraft types. */
public final class ChatHeadsSettings {
    public static final String ID = "chat_heads";
    /** Mod id of the original Chat Heads mod (R-11). */
    public static final String CHAT_HEADS_MOD = "chat_heads";

    public static final String POSITION = "position";
    public static final String BEFORE_NAME = "before_name";
    public static final String BEFORE_LINE = "before_line";

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.CHAT_SOCIAL,
            true, ApplyMode.LIVE,
            List.of(new EnumSetting(POSITION, BEFORE_NAME, List.of(BEFORE_NAME, BEFORE_LINE), ApplyMode.LIVE)),
            List.of(new ConflictDescriptor(CHAT_HEADS_MOD, "qol_unified.feature.chat_heads.conflict.chat_heads")));

    private ChatHeadsSettings() {
    }
}
