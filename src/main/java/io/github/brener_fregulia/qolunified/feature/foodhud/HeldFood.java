package io.github.brener_fregulia.qolunified.feature.foodhud;

import io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Hand;
import io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Use;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.HitResult;

/** Reads, without changing anything, which held food the player would eat on "use" (§11). */
final class HeldFood {
    private HeldFood() {
    }

    /** The food that would be eaten, or {@code null} if none would be. */
    static FoodProperties previewed(Player player, boolean offhandPreview) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        Hand hand = HandSelection.select(use(player, main, true), use(player, off, false), offhandPreview);
        return switch (hand) {
            case MAIN -> main.get(DataComponents.FOOD);
            case OFF -> off.get(DataComponents.FOOD);
            case NONE -> null;
        };
    }

    static Use use(Player player, ItemStack stack, boolean mainHand) {
        if (stack == null || stack.isEmpty()) {
            return Use.EMPTY;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        boolean consumable = stack.has(DataComponents.CONSUMABLE);
        if (food != null && consumable) {
            return player.canEat(food.canAlwaysEat()) ? Use.EDIBLE_FOOD : Use.INEDIBLE_FOOD;
        }
        if (consumable || stack.getUseAnimation() != ItemUseAnimation.NONE) {
            return Use.OTHER_USE;
        }
        // A block in the main hand is placed instead when the player looks at a block.
        HitResult hit = Minecraft.getInstance().hitResult;
        if (mainHand && stack.getItem() instanceof BlockItem && hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return Use.OTHER_USE;
        }
        return Use.NO_USE;
    }
}
