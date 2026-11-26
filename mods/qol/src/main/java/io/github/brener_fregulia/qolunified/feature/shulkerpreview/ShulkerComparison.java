package io.github.brener_fregulia.qolunified.feature.shulkerpreview;

import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.ui.FeatureComparisons;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The settings comparison for Shulker Preview (settings-ux-v1.md §8, Q-SU4): three fixed sample boxes in slots, a
 * full one, a partly filled one and a mixed one, drawn plainly and with the content icon and capacity bar. The
 * samples are built once in memory; no inventory, world or real box is read or changed.
 */
public final class ShulkerComparison implements FeatureComparisons.Comparison {
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
    private static final int SLOT_SIZE = 18;
    private final List<ItemStack> boxes = new ArrayList<>();

    public static void register() {
        FeatureComparisons.register(ShulkerPreviewSettings.ID, new ShulkerComparison());
    }

    private ShulkerComparison() {
    }

    private List<ItemStack> boxes() {
        if (boxes.isEmpty()) {
            boxes.add(box(Items.DYED_SHULKER_BOX.purple(), Collections.nCopies(27, new ItemStack(Items.COBBLESTONE, 64))));
            boxes.add(box(Items.DYED_SHULKER_BOX.blue(), List.of(new ItemStack(Items.DIAMOND, 24),
                    new ItemStack(Items.EMERALD, 64), new ItemStack(Items.EMERALD, 64))));
            boxes.add(box(Items.DYED_SHULKER_BOX.orange(), List.of(new ItemStack(Items.OAK_LOG, 64),
                    new ItemStack(Items.TORCH, 32), new ItemStack(Items.BREAD, 16), new ItemStack(Items.OAK_LOG, 64),
                    new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.TORCH, 64),
                    new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64))));
        }
        return boxes;
    }

    private static ItemStack box(net.minecraft.world.item.Item item, List<ItemStack> contents) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return stack;
    }

    @Override
    public boolean available() {
        return FeatureComparisons.itemsAvailable();
    }

    @Override
    public int width() {
        return 3 * SLOT_SIZE + 2 * 4;
    }

    @Override
    public int height() {
        return SLOT_SIZE;
    }

    @Override
    public void draw(GuiGraphicsExtractor g, Font font, int x, int y, boolean qol) {
        SettingsSnapshot settings = ShulkerPreviewFeature.activeSettings();
        String source = settings == null ? ShulkerPreviewSettings.FIRST_OCCUPIED
                : settings.getEnum(ShulkerPreviewSettings.SLOT_ICON_SOURCE);
        int sx = x;
        for (ItemStack box : boxes()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, sx, y, SLOT_SIZE, SLOT_SIZE);
            // Only the item: the vanilla side must not pick up the live slot decoration.
            g.item(box, sx + 1, y + 1);
            ShulkerSummary<ItemStack> summary = qol ? ShulkerContents.summary(box) : null;
            if (summary != null) {
                ShulkerSlotDecorator.draw(g, box, summary, source, sx + 1, y + 1);
            }
            sx += SLOT_SIZE + 4;
        }
    }
}
