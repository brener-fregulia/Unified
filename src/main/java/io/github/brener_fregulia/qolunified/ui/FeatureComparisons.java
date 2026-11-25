package io.github.brener_fregulia.qolunified.ui;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Visual "Vanilla / QoL ON" examples a feature offers for its settings panel (settings-ux-v1.md §8, Q-SU4). Internal
 * to the mod, like {@link FeatureActions}. A comparison draws a fixed sample with the game's own sprites, items and
 * text; it never reads the world or the player, so it works on the title screen.
 */
public final class FeatureComparisons {
    /** One fixed example, drawn twice: as vanilla shows it and as the feature shows it. */
    public interface Comparison {
        /** Width of one sample, in GUI pixels. */
        int width();

        /** Height of one sample, in GUI pixels. */
        int height();

        void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean qol);

        /**
         * Whether the sample can be drawn now. In Minecraft 26.3 item stacks cannot be created before item components
         * are bound, which happens only when a world is loaded; samples that need items return false on the title
         * screen and the panel shows a short note instead.
         */
        default boolean available() {
            return true;
        }
    }

    /** True once item components are bound (in a world), so item stacks can be built and drawn. */
    public static boolean itemsAvailable() {
        return net.minecraft.world.item.Items.STONE.builtInRegistryHolder().areComponentsBound();
    }

    private static final Map<String, Comparison> COMPARISONS = new ConcurrentHashMap<>();

    private FeatureComparisons() {
    }

    public static void register(String featureId, Comparison comparison) {
        COMPARISONS.put(featureId, comparison);
    }

    public static Optional<Comparison> find(String featureId) {
        return Optional.ofNullable(COMPARISONS.get(featureId));
    }
}
