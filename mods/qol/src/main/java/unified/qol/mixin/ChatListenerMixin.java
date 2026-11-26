package unified.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import unified.qol.feature.chatheads.ChatHeadMessages;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Chat Heads Mixin C-1 (chat-heads-v1.md TA-3): wraps both {@code ChatComponent.addPlayerMessage} calls in
 * {@code ChatListener.showMessageToPlayer} and replaces only the display component. The trust tag, report log and
 * narration are computed from the original message by vanilla, before or beside this call.
 */
@Mixin(ChatListener.class)
abstract class ChatListenerMixin {
    @WrapOperation(method = "showMessageToPlayer", require = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"))
    private void qolUnified$addHead(ChatComponent chat, Component content, MessageSignature signature,
            GuiMessageTag tag, Operation<Void> original, @Local(argsOnly = true) ChatType.Bound bound,
            @Local(argsOnly = true) GameProfile sender) {
        original.call(chat, ChatHeadMessages.display(content, bound.name(), sender), signature, tag);
    }
}
