package io.github.brener_fregulia.qolunified.feature.targetinfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool recommendations and the held-item mark (target-info-v1.md §0, §5). Pure: the Minecraft side probes the vanilla
 * tools with the game's own checks ({@code getDestroySpeed}, {@code isCorrectToolForDrops}) and this class only
 * combines the answers. Faster breaking, correct drops and the minimum tier are kept separate.
 */
public final class ToolAdvice {
    public enum Category {
        PICKAXE,
        AXE,
        SHOVEL,
        HOE,
        SWORD,
        SHEARS
    }

    /** Vanilla tool tiers in ascending order. Gold is left out: it has the wooden tier's drop rules. */
    public enum Tier {
        WOODEN,
        STONE,
        COPPER,
        IRON,
        DIAMOND,
        NETHERITE
    }

    public enum Mark {
        NONE,
        CHECK,
        CROSS
    }

    /**
     * What the game says about one category's vanilla tools on the block.
     *
     * @param faster whether the category's best vanilla tool breaks the block faster than the hand
     * @param minimumCorrect the lowest vanilla tier whose tool passes the correct-drops check, or {@code null} when
     *     none does (for shears, which have no tiers, {@code WOODEN} stands for "passes")
     */
    public record Probe(Category category, boolean faster, Tier minimumCorrect) {
    }

    /** A recommended tool icon: its category and the tier of the icon shown ({@code null} for shears). */
    public record Recommendation(Category category, Tier tier) {
    }

    /**
     * @param requirement the tool to name in the requirement text after a failed check, or {@code null} when no
     *     single reliable minimum is known
     */
    public record Advice(List<Recommendation> tools, Mark mark, Recommendation requirement) {
        public static final Advice NONE = new Advice(List.of(), Mark.NONE, null);

        public boolean isEmpty() {
            return tools.isEmpty() && mark == Mark.NONE;
        }
    }

    private ToolAdvice() {
    }

    /**
     * @param instant the block breaks instantly (destroy speed 0), so no tool matters
     * @param requiresCorrectTool {@code BlockState.requiresCorrectToolForDrops()}
     * @param heldCorrect {@code held.isCorrectToolForDrops(state)}: the final authority, also for modded tools
     * @param heldFaster {@code held.getDestroySpeed(state) > 1}
     */
    public static Advice evaluate(List<Probe> probes, boolean instant, boolean requiresCorrectTool,
            boolean heldCorrect, boolean heldFaster) {
        if (instant) {
            return Advice.NONE;
        }
        List<Recommendation> tools = new ArrayList<>();
        Recommendation requirement = null;
        int correctCategories = 0;
        for (Probe probe : probes) {
            boolean correct = requiresCorrectTool && probe.minimumCorrect() != null;
            if (!probe.faster() && !correct) {
                continue;
            }
            Tier tier = probe.category() == Category.SHEARS ? null
                    : correct ? probe.minimumCorrect() : Tier.WOODEN;
            Recommendation recommendation = new Recommendation(probe.category(), tier);
            tools.add(recommendation);
            if (correct) {
                correctCategories++;
                requirement = recommendation;
            }
        }
        Mark mark;
        if (requiresCorrectTool) {
            mark = heldCorrect ? Mark.CHECK : Mark.CROSS;
        } else {
            // Hand-harvestable: never wrong; a check only when the held item is one of the faster tools.
            mark = heldFaster && !tools.isEmpty() ? Mark.CHECK : Mark.NONE;
        }
        if (mark != Mark.CROSS || correctCategories != 1) {
            requirement = null;
        }
        if (tools.isEmpty() && mark == Mark.NONE) {
            return Advice.NONE;
        }
        return new Advice(List.copyOf(tools), mark, requirement);
    }
}
