package io.github.brener_fregulia.qolunified.core;

import java.util.ArrayList;
import java.util.List;

/** Test-only fixture with scriptable failures. */
final class TestFeature implements Feature {
    private final FeatureDescriptor descriptor;
    final List<String> calls = new ArrayList<>();
    int enableFailures;
    int disableFailures;
    FeatureContext lastContext;

    TestFeature(FeatureDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    static TestFeature live(String id) {
        return new TestFeature(descriptor(id, ApplyMode.LIVE, true));
    }

    static TestFeature restartRequired(String id) {
        return new TestFeature(descriptor(id, ApplyMode.RESTART_REQUIRED, true));
    }

    static FeatureDescriptor descriptor(String id, ApplyMode toggleMode, boolean defaultEnabled) {
        return new FeatureDescriptor(id, FeatureCategory.HUD_INFORMATION, defaultEnabled, toggleMode,
                List.of(
                        new SettingDescriptor.BooleanSetting("live_flag", false, ApplyMode.LIVE),
                        new SettingDescriptor.IntegerSetting("restart_size", 5, 1, 10, ApplyMode.RESTART_REQUIRED)),
                List.of());
    }

    TestFeature failEnable(int times) {
        enableFailures = times;
        return this;
    }

    TestFeature failDisable(int times) {
        disableFailures = times;
        return this;
    }

    @Override
    public FeatureDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void enable(FeatureContext context) {
        calls.add("enable");
        lastContext = context;
        if (enableFailures > 0) {
            enableFailures--;
            throw new IllegalStateException("enable failure");
        }
    }

    @Override
    public void disable() {
        calls.add("disable");
        if (disableFailures > 0) {
            disableFailures--;
            throw new IllegalStateException("disable failure");
        }
    }
}
