package io.github.brener_fregulia.qolunified.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Food HUD Mixin M-1 (food-hud-v1.md §5.2): records where {@code Hud.extractFood} drew each hunger icon, including
 * its shake offset, and draws the overlays on top when the method returns. Vanilla drawing is unchanged.
 */
@Mixin(Hud.class)
abstract class HudFoodMixin {
    @Inject(method = "extractFood", at = @At("HEAD"))
    private void qolUnified$beginFood(GuiGraphicsExtractor graphics, Player player, int y, int right,
            CallbackInfo ci) {
        FoodHudRenderer.beginFood();
    }

    // The first blitSprite call per icon draws its background at the final (shaken) position.
    @WrapOperation(method = "extractFood", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void qolUnified$recordFoodIcon(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int width, int height, Operation<Void> original) {
        original.call(graphics, pipeline, sprite, x, y, width, height);
        FoodHudRenderer.recordFoodIcon(y);
    }

    @Inject(method = "extractFood", at = @At("RETURN"))
    private void qolUnified$endFood(GuiGraphicsExtractor graphics, Player player, int y, int right,
            CallbackInfo ci) {
        FoodHudRenderer.endFood(graphics, player, y, right);
    }
}
