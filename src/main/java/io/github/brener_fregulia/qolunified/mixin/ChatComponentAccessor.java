package io.github.brener_fregulia.qolunified.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Chat Heads Mixin C-2 (chat-heads-v1.md TA-4): read access to the chat's own message list, no new state. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> qolUnified$allMessages();
}
