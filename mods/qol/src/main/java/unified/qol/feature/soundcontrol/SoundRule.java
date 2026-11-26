package unified.qol.feature.soundcontrol;

import java.util.Map;

/**
 * One sound event's rule (sound-control-v1.md §5.1): a configured volume from 0 to 100 and a separate muted flag, so
 * unmuting returns to the configured volume, also after a restart (Q-SC5).
 */
public record SoundRule(int volume, boolean muted) {
    public static final SoundRule DEFAULT = new SoundRule(100, false);

    public SoundRule {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume out of range: " + volume);
        }
    }

    /** The factor applied to vanilla's gain: 0 when muted, otherwise volume / 100; never above 1. */
    public float factor() {
        return muted ? 0f : volume / 100f;
    }

    /** True when the rule changes nothing; such rules are never stored. */
    public boolean isDefault() {
        return volume == 100 && !muted;
    }

    public SoundRule withVolume(int value) {
        return new SoundRule(value, muted);
    }

    public SoundRule withMuted(boolean value) {
        return new SoundRule(volume, value);
    }

    Map<String, Object> toEntry() {
        return Map.of(SoundControlSettings.VOLUME, volume, SoundControlSettings.MUTED, muted);
    }

    static SoundRule fromEntry(Map<String, Object> entry) {
        return new SoundRule((Integer) entry.get(SoundControlSettings.VOLUME),
                (Boolean) entry.get(SoundControlSettings.MUTED));
    }
}
