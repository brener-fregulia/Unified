package unified.qol.feature.shulkerpreview;

import unified.qol.core.SettingsSnapshot;
import unified.qol.feature.shulkerpreview.ShulkerPreviewSettings.Mode;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Tooltip data for a shulker box with known contents: the compact or the full preview (shulker-preview-v1.md §6). */
public record ShulkerTooltip(ShulkerSummary<ItemStack> summary, boolean full, int accent, boolean shiftHint)
        implements TooltipComponent {

    /** The preview mode for {@code stack} now, or {@link Mode#VANILLA} when nothing should change. */
    public static Mode mode(ItemStack stack) {
        SettingsSnapshot settings = ShulkerPreviewFeature.activeSettings();
        if (settings == null || ShulkerContents.summary(stack) == null) {
            return Mode.VANILLA;
        }
        return ShulkerPreviewSettings.mode(settings.getBoolean(ShulkerPreviewSettings.COMPACT_PREVIEW),
                settings.getBoolean(ShulkerPreviewSettings.FULL_PREVIEW_ON_SHIFT),
                Minecraft.getInstance().hasShiftDown());
    }

    /** Mixin SP-2: the preview image for a shulker box; any other stack keeps {@code vanilla}. */
    public static Optional<TooltipComponent> forStack(ItemStack stack, Optional<TooltipComponent> vanilla) {
        if (vanilla.isPresent()) {
            return vanilla;
        }
        Mode mode = mode(stack);
        if (mode == Mode.VANILLA) {
            return vanilla;
        }
        SettingsSnapshot settings = ShulkerPreviewFeature.activeSettings();
        boolean hint = mode == Mode.COMPACT && settings != null
                && settings.getBoolean(ShulkerPreviewSettings.FULL_PREVIEW_ON_SHIFT);
        return Optional.of(new ShulkerTooltip(ShulkerContents.summary(stack), mode == Mode.FULL,
                ShulkerContents.accent(stack), hint));
    }
}
