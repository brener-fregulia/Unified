package unified.qol.dev;

import unified.qol.core.ApplyMode;
import unified.qol.core.ConflictDescriptor;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configurable development-only sample feature. It performs no gameplay action and touches no world,
 * player or server state: enable and disable only record the call.
 */
public final class SampleFeature implements Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");

    private final FeatureDescriptor descriptor;
    private final List<String> calls = new ArrayList<>();
    private int enableFailures;
    private int disableFailures;

    private SampleFeature(FeatureDescriptor descriptor, int enableFailures, int disableFailures) {
        this.descriptor = descriptor;
        this.enableFailures = enableFailures;
        this.disableFailures = disableFailures;
    }

    static Builder builder(String id, FeatureCategory category) {
        return new Builder(id, category);
    }

    /** Recorded lifecycle calls, for game tests. */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public FeatureDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized void enable(FeatureContext context) {
        calls.add("enable");
        if (enableFailures > 0) {
            enableFailures--;
            throw new IllegalStateException("Sample enable failure for " + descriptor.id());
        }
        LOGGER.info("[dev] sample feature {} enabled", descriptor.id());
    }

    @Override
    public synchronized void disable() {
        calls.add("disable");
        if (disableFailures > 0) {
            disableFailures--;
            throw new IllegalStateException("Sample disable failure for " + descriptor.id());
        }
        LOGGER.info("[dev] sample feature {} disabled", descriptor.id());
    }

    static final class Builder {
        private final String id;
        private final FeatureCategory category;
        private boolean defaultEnabled = true;
        private ApplyMode toggleMode = ApplyMode.LIVE;
        private final List<SettingDescriptor> settings = new ArrayList<>();
        private final List<ConflictDescriptor> conflicts = new ArrayList<>();
        private int enableFailures;
        private int disableFailures;

        private Builder(String id, FeatureCategory category) {
            this.id = id;
            this.category = category;
        }

        Builder restartRequired() {
            toggleMode = ApplyMode.RESTART_REQUIRED;
            return this;
        }

        Builder setting(SettingDescriptor setting) {
            settings.add(setting);
            return this;
        }

        Builder conflictsWith(String modId) {
            conflicts.add(new ConflictDescriptor(modId, "qol_unified_dev.conflict." + id));
            return this;
        }

        Builder failEnable(int times) {
            enableFailures = times;
            return this;
        }

        Builder failDisable(int times) {
            disableFailures = times;
            return this;
        }

        SampleFeature build() {
            return new SampleFeature(new FeatureDescriptor(id, category, defaultEnabled, toggleMode, settings,
                    conflicts), enableFailures, disableFailures);
        }
    }
}
