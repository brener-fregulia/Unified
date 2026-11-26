package unified.qol.feature.targetinfo;

import unified.qol.core.SettingsSnapshot;
import unified.qol.ui.FeatureComparisons;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The settings comparison for Target Info (settings-ux-v1.md §18): a stone block under a crosshair, alone (vanilla)
 * and with the Target Info panel drawn by the feature's own renderer from a fixed sample view: Stone, Minecraft, the
 * wooden pickaxe recommendation and a check for a held pickaxe. No ray cast, world or player is read; the tool and
 * source rows follow the current settings.
 */
public final class TargetInfoComparison implements FeatureComparisons.Comparison {
    private static final int WIDTH = 118;
    private static final int HEIGHT = 46;
    private TargetView sample;
    private TargetView sampleWithoutTools;
    private TargetView sampleWithoutSource;
    private TargetView sampleBare;

    public static void register() {
        FeatureComparisons.register(TargetInfoSettings.ID, new TargetInfoComparison());
    }

    @Override
    public boolean available() {
        return FeatureComparisons.itemsAvailable();
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    private TargetView view(boolean tools, boolean source) {
        if (sample == null) {
            ItemStack stone = new ItemStack(Items.STONE);
            Component name = Component.translatable("block.minecraft.stone");
            Component minecraft = Component.literal("Minecraft");
            List<ItemStack> pickaxe = List.of(new ItemStack(Items.WOODEN_PICKAXE));
            sample = new TargetView(null, stone, name, minecraft, pickaxe, ToolAdvice.Mark.CHECK, List.of(), null,
                    false);
            sampleWithoutTools = new TargetView(null, stone, name, minecraft, List.of(), ToolAdvice.Mark.NONE,
                    List.of(), null, false);
            sampleWithoutSource = new TargetView(null, stone, name, null, pickaxe, ToolAdvice.Mark.CHECK, List.of(),
                    null, false);
            sampleBare = new TargetView(null, stone, name, null, List.of(), ToolAdvice.Mark.NONE, List.of(), null,
                    false);
        }
        return tools ? (source ? sample : sampleWithoutSource) : (source ? sampleWithoutTools : sampleBare);
    }

    @Override
    public void draw(GuiGraphicsExtractor g, Font font, int x, int y, boolean qol) {
        // The observed block: a stone icon under a crosshair, in the lower part of the box.
        int cx = x + WIDTH / 2;
        int blockY = y + HEIGHT - 18;
        g.item(view(true, true).icon(), cx - 8, blockY);
        g.fill(cx - 3, blockY + 7, cx + 4, blockY + 8, 0xFFFFFFFF);
        g.fill(cx, blockY + 4, cx + 1, blockY + 11, 0xFFFFFFFF);
        if (!qol) {
            return;
        }
        SettingsSnapshot settings = TargetInfoFeature.activeSettings();
        boolean tools = settings == null || settings.getBoolean(TargetInfoSettings.SHOW_TOOLS);
        boolean source = settings == null || settings.getBoolean(TargetInfoSettings.SHOW_SOURCE);
        TargetInfoRenderer.drawSample(g, font, view(tools, source), cx, y);
    }
}
