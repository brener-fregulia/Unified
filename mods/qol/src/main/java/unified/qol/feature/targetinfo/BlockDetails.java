package unified.qol.feature.targetinfo;

import static unified.qol.feature.targetinfo.TargetInfoTexts.*;

import java.util.List;

/**
 * Lines for client-known block-state values (target-info-v1.md §6). Pure: the Minecraft side reads the synced
 * block-state properties and passes plain values. "Power" is only the block's own state, never a received signal.
 */
public final class BlockDetails {
    private BlockDetails() {
    }

    /** Beehive or bee nest honey level; the bee count is not synced to clients and is never shown. */
    public static List<DetailLine> honey(int level, int max) {
        return List.of(DetailLine.of(HONEY, level, max));
    }

    /** Redstone dust: the wire's own power level, 0–15. */
    public static List<DetailLine> wire(int power) {
        return List.of(DetailLine.of(POWER, power));
    }

    public static List<DetailLine> lever(boolean powered) {
        return List.of(DetailLine.of(LEVER, DetailLine.of(powered ? ON : OFF)));
    }

    public static List<DetailLine> button(boolean powered) {
        return List.of(DetailLine.of(BUTTON, DetailLine.of(powered ? PRESSED : RELEASED)));
    }

    public static List<DetailLine> lamp(boolean lit) {
        return List.of(DetailLine.of(LAMP, DetailLine.of(lit ? ON : OFF)));
    }

    public static List<DetailLine> repeater(int delay, boolean powered, boolean locked) {
        DetailLine state = DetailLine.of(powered ? POWERED : UNPOWERED);
        return locked
                ? List.of(DetailLine.of(DELAY, delay), state, DetailLine.of(LOCKED))
                : List.of(DetailLine.of(DELAY, delay), state);
    }

    /** Comparator mode and powered flag; the output strength is server-side and not shown. */
    public static List<DetailLine> comparator(boolean subtract, boolean powered) {
        return List.of(DetailLine.of(MODE, DetailLine.of(subtract ? MODE_SUBTRACT : MODE_COMPARE)),
                DetailLine.of(powered ? POWERED : UNPOWERED));
    }

    /** Crop growth: a whole percentage while growing, "Mature" at the last age. */
    public static List<DetailLine> growth(int age, int maxAge) {
        if (maxAge <= 0) {
            return List.of();
        }
        if (age >= maxAge) {
            return List.of(DetailLine.of(MATURE));
        }
        return List.of(DetailLine.of(GROWTH, Math.max(0, age) * 100 / maxAge));
    }
}
