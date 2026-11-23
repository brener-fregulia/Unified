package io.github.brener_fregulia.qolunified.feature.chatheads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import io.github.brener_fregulia.qolunified.core.FeatureContext;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatHeadMessagesTest {
    private static final GameProfile ALEX = new GameProfile(UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce"),
            "Alex");

    private final ChatHeadsFeature feature = new ChatHeadsFeature(() -> { });
    private SettingsSnapshot settings = SettingsSnapshot.defaults(ChatHeadsSettings.DESCRIPTOR);
    private final FeatureContext context = () -> settings;

    @AfterEach
    void reset() {
        feature.disable();
        ChatHeadMessages.clear();
    }

    private void position(String value) {
        settings = settings.with("position", value);
    }

    private static GuiMessage gui(int time, Component content, MessageSignature signature, GuiMessageTag tag) {
        return new GuiMessage(time, content, signature, GuiMessageSource.PLAYER, tag);
    }

    @Test
    void inactiveFeatureShowsTheOriginalComponent() {
        Component original = Component.translatable("chat.type.text", Component.literal("Alex"), Component.literal("hi"));
        assertSame(original, ChatHeadMessages.display(original, null, ALEX));
    }

    @Test
    void noSenderMetadataMeansNoHead() {
        feature.enable(context);
        Component system = Component.literal("<Alex> hi");
        assertSame(system, ChatHeadMessages.display(system, null, null));
    }

    @Test
    void refreshSwapsOnlyTheContentAndIsIdempotent() {
        Component name = Component.literal("Alex");
        Component original = Component.translatable("chat.type.text", name, Component.literal("hi"));
        Component other = Component.literal("system line");
        var signature = new MessageSignature(new byte[256]);
        var tag = GuiMessageTag.chatNotSecure();

        Component shownWhileOff = ChatHeadMessages.display(original, name, ALEX);
        assertSame(original, shownWhileOff);
        List<GuiMessage> chat = new ArrayList<>(List.of(gui(7, shownWhileOff, signature, tag), gui(9, other, null, null)));

        feature.enable(context);
        assertTrue(ChatHeadMessages.refresh(chat, ChatHeadsFeature.position()));
        GuiMessage headed = chat.getFirst();
        assertEquals(1, ChatHeadComponents.countHeads(headed.content()));
        assertEquals(7, headed.addedTime());
        assertSame(signature, headed.signature());
        assertSame(tag, headed.tag());
        assertEquals(GuiMessageSource.PLAYER, headed.source());
        assertSame(other, chat.get(1).content(), "unknown messages untouched");
        assertEquals("<Alex> hi", headed.content().getString());

        assertFalse(ChatHeadMessages.refresh(chat, ChatHeadsFeature.position()), "no work when nothing changed");
        assertSame(headed, chat.getFirst());

        position("before_line");
        assertTrue(ChatHeadMessages.refresh(chat, ChatHeadsFeature.position()));
        assertEquals(1, ChatHeadComponents.countHeads(chat.getFirst().content()), "never stacked");
        assertSame(original, chat.getFirst().content().getSiblings().get(1));

        position("before_name");
        assertTrue(ChatHeadMessages.refresh(chat, ChatHeadsFeature.position()));
        assertSame(headed.content(), chat.getFirst().content(), "same display copy reused");

        feature.disable();
        assertTrue(ChatHeadMessages.refresh(chat, ChatHeadsFeature.position()));
        assertSame(original, chat.getFirst().content(), "original component restored");
        assertSame(signature, chat.getFirst().signature());
    }

    @Test
    void newMessagesFollowTheCurrentPosition() {
        feature.enable(context);
        Component name = Component.literal("Alex");
        Component original = Component.translatable("chat.type.text", name, Component.literal("hi"));
        Component shown = ChatHeadMessages.display(original, name, ALEX);
        assertEquals(1, ChatHeadComponents.countHeads(shown));
        assertEquals(0, ChatHeadComponents.countHeads(original), "original not mutated");
    }

    @Test
    void onlyAsManyMessagesAsVanillaKeepsAreRemembered() {
        feature.enable(context);
        Component first = Component.literal("first");
        ChatHeadMessages.display(first, null, ALEX);
        for (int i = 0; i < ChatHeadMessages.CAPACITY; i++) {
            ChatHeadMessages.display(Component.literal("m" + i), null, ALEX);
        }
        List<GuiMessage> chat = new ArrayList<>(List.of(gui(1, first, null, null)));
        assertFalse(ChatHeadMessages.refresh(chat, "before_line"), "evicted, so left alone");
    }
}
