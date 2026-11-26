package unified.qol.feature.foodhud;

import unified.qol.core.SettingsSnapshot;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Tooltip data for a food stack: its nutrition and saturation, drawn as icons by {@link FoodTooltipComponent}. */
public record FoodTooltip(int nutrition, float saturation) implements TooltipComponent {
    /**
     * The tooltip image for {@code stack} when Food HUD should add one (Mixin M-3): the stack is food, it has no
     * other tooltip image, and the tooltip mode allows it now.
     */
    public static Optional<TooltipComponent> forStack(ItemStack stack, Optional<TooltipComponent> vanilla) {
        SettingsSnapshot settings = FoodHudFeature.activeSettings();
        if (settings == null || vanilla.isPresent() || stack.isEmpty()) {
            return vanilla;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || !visible(settings.getEnum(FoodHudSettings.TOOLTIP))) {
            return vanilla;
        }
        return Optional.of(new FoodTooltip(food.nutrition(), food.saturation()));
    }

    private static boolean visible(String mode) {
        return switch (mode) {
            case FoodHudSettings.TOOLTIP_ALWAYS -> true;
            case FoodHudSettings.TOOLTIP_SHIFT -> Minecraft.getInstance().hasShiftDown();
            default -> false;
        };
    }
}
