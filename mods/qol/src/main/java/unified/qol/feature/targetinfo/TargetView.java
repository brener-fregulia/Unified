package unified.qol.feature.targetinfo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What the overlay shows for one target, built at most once per client tick (target-info-v1.md §10, §11). Mining
 * progress is not part of it: it is read every frame for a smooth bar.
 *
 * @param pos the targeted block, or {@code null} for an entity
 * @param icon the block's item, or {@link ItemStack#EMPTY}
 * @param source the friendly mod name, or {@code null} when hidden
 * @param tools the recommended tool icons, left to right
 * @param health the entity's health, or {@code null}
 * @param instant the block breaks instantly, so no progress bar
 */
public record TargetView(BlockPos pos, ItemStack icon, Component name, Component source, List<ItemStack> tools,
        ToolAdvice.Mark mark, List<Component> lines, EntityDetails.Health health, boolean instant) {
}
