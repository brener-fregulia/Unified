package unified.qol.core.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import unified.qol.core.ApplyMode;
import unified.qol.core.ConflictDescriptor;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.SettingsSnapshot;
import unified.qol.core.config.FeatureChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompatibilityReportTest {
    private static Feature feature(String id, String... conflictingMods) {
        var conflicts = new ArrayList<ConflictDescriptor>();
        for (String mod : conflictingMods) {
            conflicts.add(new ConflictDescriptor(mod, "qol_unified.conflict." + id + "." + mod.replace('-', '_')));
        }
        var descriptor = new FeatureDescriptor(id, FeatureCategory.HUD_INFORMATION, true, ApplyMode.LIVE, List.of(),
                conflicts);
        return new Feature() {
            @Override
            public FeatureDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void enable(FeatureContext context) {
            }

            @Override
            public void disable() {
            }
        };
    }

    private static FeatureRegistry registry(Feature... features) {
        var builder = FeatureRegistry.builder();
        for (Feature f : features) {
            builder.register(f);
        }
        return builder.build();
    }

    private static ModPresence installed(String... ids) {
        Set<String> set = Set.of(ids);
        return set::contains;
    }

    private static FeatureChoice implicit(FeatureRegistry registry, String id) {
        return new FeatureChoice(true, false, SettingsSnapshot.defaults(registry.require(id).descriptor()));
    }

    @Test
    void featureWithoutConflictsIsUnaffected() {
        var registry = registry(feature("plain"));
        var report = CompatibilityReport.detect(registry, installed("anything"));
        assertTrue(report.all().isEmpty());
        var choice = implicit(registry, "plain");
        assertSame(choice, report.resolve("plain", choice));
        assertFalse(report.status("plain", choice).hasConflicts());
    }

    @Test
    void declaredConflictNotInstalledIsIgnored() {
        var registry = registry(feature("hud", "other_hud"));
        var report = CompatibilityReport.detect(registry, installed("fabric-api"));
        assertTrue(report.conflicts("hud").isEmpty());
        assertTrue(report.resolve("hud", implicit(registry, "hud")).enabled());
    }

    @Test
    void installedConflictIsDetectedWithReason() {
        var registry = registry(feature("hud", "other_hud"));
        var report = CompatibilityReport.detect(registry, installed("other_hud"));
        assertEquals(List.of(new DetectedConflict("hud", "other_hud", "qol_unified.conflict.hud.other_hud")),
                report.conflicts("hud"));
    }

    @Test
    void multipleConflictsKeepDeclarationAndRegistryOrderWithoutDuplicates() {
        var registry = registry(feature("zeta", "mod_c", "mod_a", "mod_c", "mod_b"), feature("alpha", "mod_a"));
        var report = CompatibilityReport.detect(registry, installed("mod_a", "mod_b", "mod_c"));
        assertEquals(List.of("mod_c", "mod_a", "mod_b"),
                report.conflicts("zeta").stream().map(DetectedConflict::modId).toList());
        assertEquals(List.of("zeta", "zeta", "zeta", "alpha"),
                report.all().stream().map(DetectedConflict::featureId).toList());
    }

    @Test
    void noExplicitChoiceDisablesByDefault() {
        var registry = registry(feature("hud", "other_hud"));
        var report = CompatibilityReport.detect(registry, installed("other_hud"));
        var choice = implicit(registry, "hud");
        var resolved = report.resolve("hud", choice);
        assertFalse(resolved.enabled());
        assertFalse(resolved.explicitEnabled(), "a conflict default is not an explicit choice");
        var status = report.status("hud", choice);
        assertTrue(status.defaultSuppressed());
        assertFalse(status.playerOverride());
    }

    @Test
    void explicitEnableIsPreservedAndFlaggedAsOverride() {
        var registry = registry(feature("hud", "other_hud"));
        var report = CompatibilityReport.detect(registry, installed("other_hud"));
        var choice = implicit(registry, "hud").withEnabled(true);
        assertSame(choice, report.resolve("hud", choice));
        var status = report.status("hud", choice);
        assertTrue(status.playerOverride());
        assertFalse(status.defaultSuppressed());
    }

    @Test
    void explicitDisableStaysDisabled() {
        var registry = registry(feature("hud", "other_hud"), feature("plain"));
        var report = CompatibilityReport.detect(registry, installed("other_hud"));
        assertFalse(report.resolve("hud", implicit(registry, "hud").withEnabled(false)).enabled());
        assertFalse(report.resolve("plain", implicit(registry, "plain").withEnabled(false)).enabled());
        var status = report.status("hud", implicit(registry, "hud").withEnabled(false));
        assertFalse(status.playerOverride() || status.defaultSuppressed());
    }

    @Test
    void detectionDoesNotAlterRegistry() {
        var a = feature("hud", "other_hud");
        var registry = registry(a, feature("plain"));
        var before = List.copyOf(registry.features());
        var descriptor = a.descriptor();
        CompatibilityReport.detect(registry, installed("other_hud"));
        assertEquals(before, registry.features());
        assertSame(descriptor, registry.require("hud").descriptor());
    }

    @Test
    void failingPresenceCheckIsIsolated() {
        var registry = registry(feature("broken", "bad_mod", "good_mod"), feature("other", "good_mod"));
        ModPresence presence = modId -> {
            if (modId.equals("bad_mod")) {
                throw new IllegalStateException("lookup failure");
            }
            return true;
        };
        var report = CompatibilityReport.detect(registry, presence);
        assertEquals(List.of("good_mod"), report.conflicts("broken").stream().map(DetectedConflict::modId).toList());
        assertEquals(1, report.conflicts("other").size());
    }

    @Test
    void reportIsImmutable() {
        var registry = registry(feature("hud", "other_hud"));
        var report = CompatibilityReport.detect(registry, installed("other_hud"));
        var list = report.conflicts("hud");
        try {
            list.clear();
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, report.conflicts("hud").size());
            return;
        }
        throw new AssertionError("conflict list must be immutable");
    }
}
