package unified.qol.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.FailureRecord.Operation;
import unified.qol.core.TransitionOutcome.Kind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureLifecycleTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC);

    private static FeatureLifecycle started(Feature... features) {
        var builder = FeatureRegistry.builder();
        for (Feature f : features) {
            builder.register(f);
        }
        var lifecycle = new FeatureLifecycle(builder.build(), Map.of(), CLOCK);
        lifecycle.startup();
        return lifecycle;
    }

    private static Map<String, DesiredFeature> toggle(TestFeature f, boolean enabled, FeatureLifecycle l) {
        return Map.of(f.descriptor().id(), new DesiredFeature(enabled, l.desiredSettings(f.descriptor().id())));
    }

    @Test
    void startupEnablesDesiredFeaturesOnly() {
        var on = TestFeature.live("on");
        var off = new TestFeature(TestFeature.descriptor("off", ApplyMode.LIVE, false));
        var l = started(on, off);
        assertEquals(FeatureState.ACTIVE, l.state("on"));
        assertEquals(FeatureState.INACTIVE, l.state("off"));
        assertEquals(List.of(), off.calls);
        assertThrows(IllegalStateException.class, l::startup);
    }

    @Test
    void liveToggleOffAndOnCallsEachOperationOnce() {
        var f = TestFeature.live("feat");
        var l = started(f);
        var before = l.desiredSettings("feat");
        assertEquals(Kind.DISABLED, l.apply(toggle(f, false, l)).outcomes().getFirst().kind());
        assertEquals(Kind.ENABLED, l.apply(toggle(f, true, l)).outcomes().getFirst().kind());
        assertEquals(List.of("enable", "disable", "enable"), f.calls);
        assertEquals(FeatureState.ACTIVE, l.state("feat"));
        assertEquals(before, l.desiredSettings("feat"));
    }

    @Test
    void unchangedDesiredStateIsNoOp() {
        var f = TestFeature.live("feat");
        var l = started(f);
        assertTrue(l.apply(toggle(f, true, l)).outcomes().isEmpty());
        assertTrue(l.apply(Map.of()).outcomes().isEmpty());
        assertEquals(List.of("enable"), f.calls);
    }

    @Test
    void failedEnableWithSuccessfulRollbackIsInactive() {
        var f = TestFeature.live("feat").failEnable(1);
        var l = started(f);
        assertEquals(FeatureState.INACTIVE, l.state("feat"));
        assertEquals(List.of("enable", "disable"), f.calls);
        var failure = l.failure("feat").orElseThrow();
        assertEquals(Operation.ENABLE, failure.operation());
        assertFalse(failure.rollbackFailed());
        assertEquals(IllegalStateException.class.getName(), failure.exceptionType());
        assertEquals(CLOCK.instant(), failure.time());
        assertTrue(l.desiredEnabled("feat"), "desired state is preserved");
    }

    @Test
    void failedEnableWithFailedRollbackIsUnknown() {
        var f = TestFeature.live("feat").failEnable(1).failDisable(1);
        var l = started(f);
        assertEquals(FeatureState.UNKNOWN, l.state("feat"));
        assertTrue(l.failure("feat").orElseThrow().rollbackFailed());
    }

    @Test
    void failedDisableIsUnknownNeverInactive() {
        var f = TestFeature.live("feat").failDisable(1);
        var l = started(f);
        var report = l.apply(toggle(f, false, l));
        assertTrue(report.hasFailures());
        assertEquals(Kind.DISABLE_FAILED, report.outcomes().getFirst().kind());
        assertEquals(FeatureState.UNKNOWN, l.state("feat"));
        assertEquals(Operation.DISABLE, l.failure("feat").orElseThrow().operation());
        assertFalse(l.desiredEnabled("feat"), "saved choice is preserved");
    }

    @Test
    void manualRetryOfDisableClearsFailure() {
        var f = TestFeature.live("feat").failDisable(2);
        var l = started(f);
        l.apply(toggle(f, false, l));
        assertEquals(Kind.DISABLE_FAILED, l.retryDisable("feat").kind());
        assertEquals(FeatureState.UNKNOWN, l.state("feat"));
        assertEquals(Kind.DISABLED, l.retryDisable("feat").kind());
        assertEquals(FeatureState.INACTIVE, l.state("feat"));
        assertTrue(l.failure("feat").isEmpty());
        assertThrows(IllegalStateException.class, () -> l.retryDisable("feat"));
    }

    @Test
    void enableIsRejectedWhileUnknown() {
        var f = TestFeature.live("feat").failDisable(1);
        var l = started(f);
        l.apply(toggle(f, false, l));
        int calls = f.calls.size();
        var report = l.apply(toggle(f, true, l));
        assertEquals(Kind.REJECTED_UNKNOWN, report.outcomes().getFirst().kind());
        assertEquals(calls, f.calls.size(), "no lifecycle call");
        assertEquals(FeatureState.UNKNOWN, l.state("feat"));
    }

    @Test
    void reapplyingAfterFailureDoesNotRetry() {
        var f = TestFeature.live("feat").failDisable(5);
        var l = started(f);
        l.apply(toggle(f, false, l));
        int calls = f.calls.size();
        // Acknowledging or closing the results screen re-applies the same desired state.
        assertTrue(l.apply(toggle(f, false, l)).outcomes().isEmpty());
        assertEquals(calls, f.calls.size());

        var g = TestFeature.live("geat").failEnable(5);
        var lg = started(g);
        int gCalls = g.calls.size();
        assertTrue(lg.apply(toggle(g, true, lg)).outcomes().isEmpty());
        assertEquals(gCalls, g.calls.size());
    }

    @Test
    void failuresAreIsolatedBetweenFeatures() {
        var bad = TestFeature.live("bad").failEnable(1).failDisable(1);
        var good = TestFeature.live("good");
        var l = started(bad, good);
        assertEquals(FeatureState.UNKNOWN, l.state("bad"));
        assertEquals(FeatureState.ACTIVE, l.state("good"));
    }

    @Test
    void oneFailingAndOneSucceedingChangeInSameApply() {
        var bad = TestFeature.live("bad").failDisable(1);
        var good = TestFeature.live("good");
        var l = started(bad, good);
        var report = l.apply(Map.of(
                "bad", new DesiredFeature(false, l.desiredSettings("bad")),
                "good", new DesiredFeature(false, l.desiredSettings("good"))));
        assertTrue(report.hasFailures());
        assertEquals(FeatureState.UNKNOWN, l.state("bad"));
        assertEquals(FeatureState.INACTIVE, l.state("good"));
    }

    @Test
    void disablesRunBeforeEnables() {
        var a = new TestFeature(TestFeature.descriptor("alpha", ApplyMode.LIVE, false));
        var b = TestFeature.live("beta");
        var l = started(a, b);
        var report = l.apply(Map.of(
                "alpha", new DesiredFeature(true, l.desiredSettings("alpha")),
                "beta", new DesiredFeature(false, l.desiredSettings("beta"))));
        assertEquals(List.of("beta", "alpha"), report.outcomes().stream().map(TransitionOutcome::featureId).toList());
    }

    @Test
    void restartRequiredToggleDoesNotCallLifecycle() {
        var f = TestFeature.restartRequired("feat");
        var l = started(f);
        var report = l.apply(toggle(f, false, l));
        assertEquals(Kind.PENDING_RESTART, report.outcomes().getFirst().kind());
        assertEquals(List.of("enable"), f.calls);
        assertEquals(FeatureState.ACTIVE, l.state("feat"));
        assertFalse(l.desiredEnabled("feat"));
        assertTrue(l.pendingRestart("feat"));
        l.apply(toggle(f, true, l));
        assertFalse(l.pendingRestart("feat"), "back to the startup state");
    }

    @Test
    void liveSettingsTakeEffectImmediately() {
        var f = TestFeature.live("feat");
        var l = started(f);
        var desired = l.desiredSettings("feat").with("live_flag", true);
        l.apply(Map.of("feat", new DesiredFeature(true, desired)));
        assertTrue(l.effectiveSettings("feat").getBoolean("live_flag"));
        assertTrue(f.lastContext.settings().getBoolean("live_flag"), "feature sees the new snapshot");
        assertFalse(l.pendingRestart("feat"));
    }

    @Test
    void restartRequiredSettingsKeepPreviousEffectiveValue() {
        var f = TestFeature.live("feat");
        var l = started(f);
        var desired = l.desiredSettings("feat").with("live_flag", true).with("restart_size", 9);
        var report = l.apply(Map.of("feat", new DesiredFeature(true, desired)));
        assertEquals(Kind.PENDING_RESTART, report.outcomes().getFirst().kind());
        assertEquals(9, l.desiredSettings("feat").getInt("restart_size"));
        assertEquals(5, l.effectiveSettings("feat").getInt("restart_size"));
        assertTrue(l.effectiveSettings("feat").getBoolean("live_flag"));
        assertTrue(l.pendingRestart("feat"));
        assertEquals(List.of("enable"), f.calls);
    }

    @Test
    void invalidApplyChangesNothing() {
        var f = TestFeature.live("feat");
        var l = started(f);
        var foreign = SettingsSnapshot.defaults(TestFeature.descriptor("feat", ApplyMode.LIVE, true));
        assertThrows(IllegalArgumentException.class, () -> l.apply(Map.of(
                "feat", new DesiredFeature(false, l.desiredSettings("feat")),
                "missing", new DesiredFeature(false, foreign))));
        assertTrue(l.desiredEnabled("feat"));
        assertEquals(List.of("enable"), f.calls);
    }

    @Test
    void initialDesiredStateIsUsedAtStartup() {
        var f = TestFeature.live("feat");
        var registry = FeatureRegistry.builder().register(f).build();
        var settings = SettingsSnapshot.defaults(f.descriptor()).with("restart_size", 8);
        var l = new FeatureLifecycle(registry, Map.of("feat", new DesiredFeature(false, settings)), CLOCK);
        l.startup();
        assertEquals(FeatureState.INACTIVE, l.state("feat"));
        assertEquals(8, l.effectiveSettings("feat").getInt("restart_size"));
        assertThrows(IllegalArgumentException.class, () -> new FeatureLifecycle(registry,
                Map.of("unknown", new DesiredFeature(false, settings)), CLOCK));
    }
}
