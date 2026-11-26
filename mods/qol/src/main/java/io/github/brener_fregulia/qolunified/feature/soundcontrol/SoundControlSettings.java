package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.ConflictDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.BooleanSetting;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.IntegerSetting;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor.MapSetting;
import java.util.List;
import java.util.regex.Pattern;

/** Sound Control descriptor and setting keys (sound-control-v1.md §0, §8). Free of Minecraft types. */
public final class SoundControlSettings {
    public static final String ID = "sound_control";
    public static final String RULES = "event_rules";
    public static final String VOLUME = "volume";
    public static final String MUTED = "muted";

    /** Q-SC6. */
    public static final int MAX_RULES = 4096;
    public static final int RECENT_CAPACITY = 128;

    /** Minecraft identifier syntax with an explicit namespace, at most 256 characters (§8.1). */
    public static final Pattern EVENT_ID = Pattern.compile("(?=.{3,256}$)[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Fabric mod ids, read from the published 26.3 JARs' fabric.mod.json (§2.1). */
    public static final String EXTREME_SOUND_MUFFLER = "extremesoundmuffler";
    public static final String SOUND_CONTROLLER = "soundcontroller";
    public static final String DECIBEL = "decibel";

    public static final MapSetting RULES_SETTING = new MapSetting(RULES, EVENT_ID, MAX_RULES,
            List.of(new IntegerSetting(VOLUME, 100, 0, 100, ApplyMode.LIVE),
                    new BooleanSetting(MUTED, false, ApplyMode.LIVE)),
            ApplyMode.LIVE);

    public static final FeatureDescriptor DESCRIPTOR = new FeatureDescriptor(ID, FeatureCategory.AUDIO, true,
            ApplyMode.LIVE, List.of(RULES_SETTING),
            List.of(new ConflictDescriptor(EXTREME_SOUND_MUFFLER, SoundControlTexts.CONFLICT_ESM),
                    new ConflictDescriptor(SOUND_CONTROLLER, SoundControlTexts.CONFLICT_SOUND_CONTROLLER),
                    new ConflictDescriptor(DECIBEL, SoundControlTexts.CONFLICT_DECIBEL)));

    private SoundControlSettings() {
    }
}
