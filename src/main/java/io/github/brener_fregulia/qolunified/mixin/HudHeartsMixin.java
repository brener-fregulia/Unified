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
 * Food HUD Mixin M-2 (food-hud-v1.md §5.2): records where {@code Hud.extractHearts} drew each heart container,
 * including shake and regeneration offsets, and draws the health preview on top when the method returns.
 *
 * <p>Positions are read from the single sprite call in {@code Hud.extractHeart}, which never sees
 * {@code Hud.HeartType} (not accessible from here); container sprites are recognised by their vanilla id. Only calls
 * made while {@code extractHearts} runs are recorded. Vanilla drawing is unchanged.
 */
@Mixin(Hud.class)
abstract class HudHeartsMixin {
    @Inject(method = "extractHearts", at = @At("HEAD"))
    private void qolUnified$beginHearts(GuiGraphicsExtractor graphics, Player player, int left, int top,
            int rowHeight, int regenerationIndex, float maxHealth, int health, int displayHealth, int absorption,
            boolean blinking, CallbackInfo ci) {
        FoodHudRenderer.beginHearts();
    }

    @WrapOperation(method = "extractHeart", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void qolUnified$recordHeart(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int width, int height, Operation<Void> original) {
        original.call(graphics, pipeline, sprite, x, y, width, height);
        FoodHudRenderer.recordHeartSprite(sprite, x, y);
    }

    @Inject(method = "extractHearts", at = @At("RETURN"))
    private void qolUnified$endHearts(GuiGraphicsExtractor graphics, Player player, int left, int top,
            int rowHeight, int regenerationIndex, float maxHealth, int health, int displayHealth, int absorption,
            boolean blinking, CallbackInfo ci) {
        FoodHudRenderer.endHearts(graphics, player, left, top, rowHeight);
    }
}
