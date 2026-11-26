package unified.qol.feature.soundcontrol;

import unified.qol.core.Feature;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingsSnapshot;
import java.util.Map;
import java.util.Objects;

/**
 * Sound Control (sound-control-v1.md). The Core replaces the effective settings snapshot after a confirmed save
 * (M0 §3.2), so {@link #sync()} runs every client tick and publishes new rules when the snapshot changed. Enabling
 * publishes the committed rules; disabling publishes none, so playing sounds return to vanilla volume.
 */
public final class SoundControlFeature implements Feature {
    /** The Minecraft side: the rule table on the audio path, the history listener and the entry points. */
    interface Runtime {
        /** Called once, on the first enable. */
        void install(SoundControlFeature feature);

        /** Publishes {@code rules} to the audio path and refreshes playing sounds. */
        void publish(Map<String, SoundRule> rules);

        /** The feature became active: start the history. */
        void start();

        /** The feature became inactive: stop the history and end any preview. */
        void stop();
    }

    private static volatile FeatureContext active;

    private final Runtime runtime;
    private boolean installed;
    private SettingsSnapshot published;

    public SoundControlFeature() {
        // Resolved only on the first enable, so loading the registry does not load sound classes.
        this(new Runtime() {
            @Override
            public void install(SoundControlFeature feature) {
                SoundControlHooks.install(feature);
            }

            @Override
            public void publish(Map<String, SoundRule> rules) {
                SoundControlHooks.publish(rules);
            }

            @Override
            public void start() {
                SoundControlHooks.start();
            }

            @Override
            public void stop() {
                SoundControlHooks.stop();
            }
        });
    }

    SoundControlFeature(Runtime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public FeatureDescriptor descriptor() {
        return SoundControlSettings.DESCRIPTOR;
    }

    @Override
    public void enable(FeatureContext context) {
        Objects.requireNonNull(context, "context");
        if (!installed) {
            runtime.install(this);
            installed = true;
        }
        active = context;
        runtime.start();
        sync();
    }

    @Override
    public void disable() {
        active = null;
        published = null;
        if (installed) {
            runtime.stop();
            runtime.publish(Map.of());
        }
    }

    /** Publishes the committed rules if the effective settings changed since the last publication. Client thread. */
    void sync() {
        FeatureContext context = active;
        if (context == null) {
            return;
        }
        SettingsSnapshot current = context.settings();
        if (current == published) {
            return;
        }
        boolean unchanged = published != null && current.equals(published);
        published = current;
        if (unchanged) {
            return;
        }
        runtime.publish(SoundRules.read(current.get(SoundControlSettings.RULES)));
    }

    /** The current effective settings, or {@code null} while the feature is inactive. */
    public static SettingsSnapshot activeSettings() {
        FeatureContext context = active;
        return context == null ? null : context.settings();
    }
}
