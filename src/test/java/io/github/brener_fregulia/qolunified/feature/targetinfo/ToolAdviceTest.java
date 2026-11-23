package io.github.brener_fregulia.qolunified.feature.targetinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Advice;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Category;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Mark;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Probe;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Recommendation;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Tier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The probes below mirror what the vanilla tools answer for each block; the game tests check the real answers. */
class ToolAdviceTest {
    private static List<Probe> probes(Probe... relevant) {
        List<Probe> all = new ArrayList<>();
        for (Category c : Category.values()) {
            Probe p = new Probe(c, false, null);
            for (Probe r : relevant) {
                if (r.category() == c) {
                    p = r;
                }
            }
            all.add(p);
        }
        return all;
    }

    private static final List<Probe> STONE = probes(new Probe(Category.PICKAXE, true, Tier.WOODEN));
    private static final List<Probe> OBSIDIAN = probes(new Probe(Category.PICKAXE, true, Tier.DIAMOND));
    private static final List<Probe> GRASS = probes(new Probe(Category.SHOVEL, true, null));
    private static final List<Probe> LEAVES = probes(new Probe(Category.HOE, true, null),
            new Probe(Category.SHEARS, true, null));
    private static final List<Probe> COBWEB = probes(new Probe(Category.SWORD, true, Tier.WOODEN),
            new Probe(Category.SHEARS, true, Tier.WOODEN));

    @Test
    void stoneNeedsAPickaxeForDrops() {
        Advice hand = ToolAdvice.evaluate(STONE, false, true, false, false);
        assertEquals(List.of(new Recommendation(Category.PICKAXE, Tier.WOODEN)), hand.tools());
        assertEquals(Mark.CROSS, hand.mark());
        assertEquals(new Recommendation(Category.PICKAXE, Tier.WOODEN), hand.requirement());

        Advice pickaxe = ToolAdvice.evaluate(STONE, false, true, true, true);
        assertEquals(Mark.CHECK, pickaxe.mark());
        assertNull(pickaxe.requirement());
    }

    @Test
    void obsidianTierFailureNamesTheMinimumTier() {
        Advice iron = ToolAdvice.evaluate(OBSIDIAN, false, true, false, true);
        assertEquals(List.of(new Recommendation(Category.PICKAXE, Tier.DIAMOND)), iron.tools());
        assertEquals(Mark.CROSS, iron.mark(), "faster but not correct for drops");
        assertEquals(Tier.DIAMOND, iron.requirement().tier());
        assertEquals(Mark.CHECK, ToolAdvice.evaluate(OBSIDIAN, false, true, true, true).mark());
    }

    @Test
    void handHarvestableBlocksAreNeverMarkedWrong() {
        Advice hand = ToolAdvice.evaluate(GRASS, false, false, true, false);
        assertEquals(List.of(new Recommendation(Category.SHOVEL, Tier.WOODEN)), hand.tools());
        assertEquals(Mark.NONE, hand.mark(), "a faster tool exists, but the hand is not wrong");
        assertNull(hand.requirement());
        assertEquals(Mark.CHECK, ToolAdvice.evaluate(GRASS, false, false, true, true).mark());
    }

    @Test
    void multipleToolsAppearSideBySide() {
        Advice leaves = ToolAdvice.evaluate(LEAVES, false, false, true, false);
        assertEquals(List.of(new Recommendation(Category.HOE, Tier.WOODEN), new Recommendation(Category.SHEARS, null)),
                leaves.tools());
        assertEquals(Mark.NONE, leaves.mark());
    }

    @Test
    void twoCorrectCategoriesGiveNoSingleRequirement() {
        Advice hand = ToolAdvice.evaluate(COBWEB, false, true, false, false);
        assertEquals(2, hand.tools().size());
        assertEquals(Mark.CROSS, hand.mark());
        assertNull(hand.requirement(), "no single tool to name");
    }

    @Test
    void toolFreeAndInstantBlocksShowNothing() {
        assertSame(Advice.NONE, ToolAdvice.evaluate(probes(), false, false, true, false), "torch-like");
        assertSame(Advice.NONE, ToolAdvice.evaluate(STONE, true, true, false, false), "instant break");
    }

    @Test
    void theGamesCheckWinsForUnknownRequirements() {
        // A modded block that no vanilla tool harvests: the game's own check is still the authority.
        Advice modded = ToolAdvice.evaluate(probes(), false, true, false, false);
        assertEquals(List.of(), modded.tools());
        assertEquals(Mark.CROSS, modded.mark());
        assertNull(modded.requirement(), "no tier is invented");
        assertEquals(Mark.CHECK, ToolAdvice.evaluate(probes(), false, true, true, false).mark(), "modded tool");
    }
}
