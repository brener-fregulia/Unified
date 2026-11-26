package unified.qol.ui;

import unified.qol.core.ApplyMode;
import unified.qol.core.ConflictDescriptor;
import unified.qol.core.CoreRuntime;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.SettingDescriptor;
import unified.qol.core.config.ConfigurationService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Test-only features for populating the settings model. Never shipped. */
final class UiFixtures {
    private UiFixtures() {
    }

    static final class Fixture implements Feature {
        final FeatureDescriptor descriptor;
        final List<String> calls = new ArrayList<>();
        int enableFailures;
        int disableFailures;

        Fixture(String id, FeatureCategory category, boolean defaultEnabled, List<ConflictDescriptor> conflicts) {
            descriptor = new FeatureDescriptor(id, category, defaultEnabled, ApplyMode.LIVE, List.of(
                    new SettingDescriptor.BooleanSetting("live_flag", false, ApplyMode.LIVE),
                    new SettingDescriptor.IntegerSetting("size", 5, 1, 10, ApplyMode.RESTART_REQUIRED),
                    new SettingDescriptor.EnumSetting("mode", "a", List.of("a", "b"), ApplyMode.LIVE)), conflicts);
        }

        @Override
        public FeatureDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void enable(FeatureContext context) {
            calls.add("enable");
            if (enableFailures-- > 0) {
                throw new IllegalStateException("fixture enable failure");
            }
        }

        @Override
        public void disable() {
            calls.add("disable");
            if (disableFailures-- > 0) {
                throw new IllegalStateException("fixture disable failure");
            }
        }
    }

    static Fixture feature(String id, FeatureCategory category) {
        return new Fixture(id, category, true, List.of());
    }

    static Fixture conflicting(String id, String modId) {
        return new Fixture(id, FeatureCategory.HUD_INFORMATION, true,
                List.of(new ConflictDescriptor(modId, "qol_unified.conflict.test")));
    }

    static CoreRuntime start(Path dir, Set<String> installed, Feature... features) {
        var builder = FeatureRegistry.builder();
        for (Feature f : features) {
            builder.register(f);
        }
        FeatureRegistry registry = builder.build();
        return CoreRuntime.start(registry, new ConfigurationService(dir.resolve("config.json"), registry),
                installed::contains, Thread.currentThread());
    }
}
