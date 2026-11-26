package unified.qol.feature.shulkerpreview;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The compact preview's Shift hint (shulker-preview-v1.md §15): the key name styled gold, the rest left unstyled so it
 * takes the tooltip's muted hint color. One line, built from translation keys.
 */
public final class ShulkerHint {
    private ShulkerHint() {
    }

    public static Component text() {
        Component key = Component.translatable(ShulkerPreviewTexts.SHIFT_KEY).withStyle(ChatFormatting.GOLD);
        return Component.translatable(ShulkerPreviewTexts.SHIFT_HINT, key);
    }
}
