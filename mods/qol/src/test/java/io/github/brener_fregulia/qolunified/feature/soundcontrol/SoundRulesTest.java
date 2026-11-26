package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rule matching, factors and the stored representation (sound-control-v1.md §5.1, §8.1). */
class SoundRulesTest {
    @Test
    void factorsNeverExceedVanilla() {
        assertEquals(0f, new SoundRule(100, true).factor());
        assertEquals(0f, new SoundRule(0, false).factor());
        assertEquals(0.25f, new SoundRule(25, false).factor());
        assertEquals(0.5f, new SoundRule(50, false).factor());
        assertEquals(1f, new SoundRule(100, false).factor());
        assertThrows(IllegalArgumentException.class, () -> new SoundRule(101, false));
        assertThrows(IllegalArgumentException.class, () -> new SoundRule(-1, false));
        assertTrue(SoundRule.DEFAULT.isDefault());
        assertFalse(new SoundRule(100, true).isDefault());
    }

    @Test
    void muteAndVolumeAreIndependent() {
        SoundRule rule = new SoundRule(25, false).withMuted(true);
        assertEquals(25, rule.volume());
        assertEquals(new SoundRule(40, true), rule.withVolume(40), "moving the slider keeps the sound muted");
        assertEquals(0.25f, rule.withMuted(false).factor());
    }

    @Test
    void writeDropsDefaultsAndRoundTrips() {
        var rules = Map.of("a:x", new SoundRule(25, true), "a:y", SoundRule.DEFAULT, "a:z", new SoundRule(100, true));
        var value = SoundRules.write(rules);
        assertEquals(Map.of("a:x", Map.of("volume", 25, "muted", true), "a:z", Map.of("volume", 100, "muted", true)),
                value);
        assertTrue(SoundControlSettings.RULES_SETTING.accepts(value));
        assertEquals(Map.of("a:x", new SoundRule(25, true), "a:z", new SoundRule(100, true)), SoundRules.read(value));
        assertEquals(Map.of(), SoundRules.read(Map.of("a:y", Map.of("volume", 100, "muted", false))),
                "a stored default entry is no rule");
    }

    @Test
    void tableMatchesExactIdsOnly() {
        var table = SoundRules.table(Map.of("minecraft:entity.tnt.primed", new SoundRule(0, false),
                "minecraft:entity.villager.ambient", new SoundRule(50, false), "bad id", new SoundRule(10, false)),
                id -> id.contains(" ") ? null : id);
        assertEquals(0f, SoundRules.factor(table, "minecraft:entity.tnt.primed"));
        assertEquals(1f, SoundRules.factor(table, "minecraft:entity.creeper.primed"), "same asset, other event");
        assertEquals(1f, SoundRules.factor(table, "minecraft:entity.generic.explode"));
        assertEquals(0.5f, SoundRules.factor(table, "minecraft:entity.villager.ambient"));
        assertEquals(1f, SoundRules.factor(table, "minecraft:entity.villager"), "no prefix matching");
        assertEquals(1f, SoundRules.factor(table, null), "a null id is safe");
        assertEquals(2, table.size(), "unparsable ids are skipped");
        assertEquals(Map.of(), SoundRules.table(Map.of(), id -> id));
        assertEquals(1f, SoundRules.factor(Map.<String, Float>of(), "a:b"));
    }

    @Test
    void previewOverridesOnlyWhatDiffersFromCommitted() {
        var committed = Map.of("a:x", new SoundRule(25, false), "a:y", new SoundRule(50, true));
        assertEquals(Map.of(), SoundRules.overrides(committed, committed), "no edits, no overrides");

        var edited = Map.of("a:x", new SoundRule(25, true), "a:y", new SoundRule(30, true), "a:z",
                new SoundRule(60, false));
        assertEquals(Map.of("a:x", 0f, "a:z", 0.6f), SoundRules.overrides(edited, committed),
                "a:y stays muted: same audible factor, no override");

        assertEquals(Map.of("a:x", 1f, "a:y", 1f), SoundRules.overrides(Map.of(), committed),
                "reset all previews vanilla for every committed rule");
        assertEquals(0.5f, SoundRules.override(new SoundRule(50, false), null));
        assertEquals(1f, SoundRules.override(null, new SoundRule(10, false)), "reset of a committed rule");
        assertEquals(null, SoundRules.override(SoundRule.DEFAULT, null));
        assertEquals(null, SoundRules.override(new SoundRule(25, true), new SoundRule(80, true)));
    }

    @Test
    void keyedSkipsUnparsableIds() {
        var keyed = SoundRules.keyed(Map.of("a:b", 0.5f, "bad id", 0f), id -> id.contains(" ") ? null : id);
        assertEquals(Map.of("a:b", 0.5f), keyed);
        assertEquals(Map.of(), SoundRules.keyed(Map.of(), id -> id));
    }

    @Test
    void eventIdPatternFollowsIdentifierSyntax() {
        var p = SoundControlSettings.EVENT_ID;
        assertTrue(p.matcher("minecraft:entity.generic.explode").matches());
        assertTrue(p.matcher("my_mod:machines/hum-2").matches());
        assertFalse(p.matcher("entity.generic.explode").matches(), "a namespace is required");
        assertFalse(p.matcher("Minecraft:Explode").matches(), "upper case");
        assertFalse(p.matcher("a:b c").matches());
        assertFalse(p.matcher("a:" + "x".repeat(255)).matches(), "at most 256 characters");
        assertTrue(p.matcher("a:" + "x".repeat(254)).matches());
    }
}
