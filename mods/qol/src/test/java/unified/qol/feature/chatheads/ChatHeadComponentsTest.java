package unified.qol.feature.chatheads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import org.junit.jupiter.api.Test;

class ChatHeadComponentsTest {
    private static final GameProfile STEVE = new GameProfile(UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"),
            "Steve");

    private static MutableComponent name() {
        return Component.literal("Steve").withStyle(s -> s
                .withClickEvent(new ClickEvent.SuggestCommand("/tell Steve "))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Steve"))));
    }

    /** Vanilla {@code chat.type.text}: translatable "<%s> %s" with the bound name and the content. */
    private static Component vanilla(Component name, Component content) {
        return Component.translatable("chat.type.text", name, content);
    }

    /** A recognizable stand-in for the head glyph, so the structure can be checked by identity. */
    private static final Component HEAD = Component.literal("@");

    @Test
    void headIsTheVanillaPlayerGlyphWithHatAndTextFallbackUnchanged() {
        Component head = ChatHeadComponents.head(STEVE);
        var object = assertInstanceOf(ObjectContents.class, head.getContents());
        var sprite = assertInstanceOf(PlayerSprite.class, object.contents());
        assertTrue(sprite.hat(), "hat layer");
        assertEquals(STEVE, sprite.player().partialProfile());
        assertEquals("", head.getString(), "plain text and logs are unchanged");
        assertEquals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.WHITE), head.getStyle().getColor(),
                "not tinted by a colored message");
        assertFalse(head.getStyle().applyTo(Style.EMPTY.withItalic(true).withBold(true)).isItalic(), "upright");
        assertFalse(head.getStyle().applyTo(Style.EMPTY.withItalic(true).withBold(true)).isBold(), "not bold");
        Component message = ChatHeadComponents.withHead(vanilla(name(), Component.literal("hi")), null, head,
                "before_line");
        assertEquals(1, ChatHeadComponents.countHeads(message));
    }

    @Test
    void beforeNameInsertsDirectlyBeforeTheNameArgument() {
        MutableComponent name = name();
        Component content = Component.literal("Hello").withStyle(ChatFormatting.GOLD);
        Component original = vanilla(name, content);
        Component shown = ChatHeadComponents.withHead(original, name, HEAD, "before_name");

        var tr = assertInstanceOf(TranslatableContents.class, shown.getContents());
        assertEquals("chat.type.text", tr.getKey());
        Component wrapped = (Component) tr.getArgs()[0];
        assertEquals(java.util.List.of(HEAD, name), wrapped.getSiblings());
        assertSame(name, wrapped.getSiblings().get(1), "name component kept, with its click and hover");
        assertSame(content, tr.getArgs()[1], "message content shared, not copied");
        assertEquals("<@Steve> Hello", shown.getString(), "no space added");
        assertEquals("<Steve> Hello", original.getString(), "original untouched");
        assertSame(name, ((TranslatableContents) original.getContents()).getArgs()[0]);
    }

    @Test
    void nestedStyledNameIsFoundByIdentityAndStylesArePreserved() {
        MutableComponent name = name();
        // A custom format: [Team] <prefix + name> : text, with the name nested inside a styled sibling.
        MutableComponent prefixed = Component.literal("").withStyle(ChatFormatting.RED)
                .append(Component.literal("[VIP] ")).append(name);
        MutableComponent root = Component.literal("[Team] ").withStyle(ChatFormatting.BLUE);
        root.append(Component.translatableWithFallback("custom.fmt", "%s: %s", prefixed, Component.literal("text"))
                .withStyle(ChatFormatting.ITALIC));
        Component shown = ChatHeadComponents.withHead(root, name, HEAD, "before_name");

        assertEquals("[Team] [VIP] @Steve: text", shown.getString(), "head right before the name");
        assertEquals(root.getStyle(), shown.getStyle());
        Component translated = shown.getSiblings().getFirst();
        assertEquals(root.getSiblings().getFirst().getStyle(), translated.getStyle());
        Component newPrefixed = (Component) ((TranslatableContents) translated.getContents()).getArgs()[0];
        assertEquals(prefixed.getStyle(), newPrefixed.getStyle());
        assertSame(prefixed.getSiblings().getFirst(), newPrefixed.getSiblings().getFirst());
    }

    @Test
    void beforeLinePutsTheHeadFirstAndKeepsTheWholeMessage() {
        MutableComponent name = name();
        Component original = vanilla(name, Component.literal("hi"));
        Component shown = ChatHeadComponents.withHead(original, name, HEAD, "before_line");
        assertEquals(java.util.List.of(HEAD, original), shown.getSiblings());
        assertSame(original, shown.getSiblings().get(1));
        assertEquals("@<Steve> hi", shown.getString());
    }

    @Test
    void missingNameFallsBackToBeforeLineWithoutReadingText() {
        // Same text as the name, but a different component: identity, not text, decides.
        Component original = vanilla(Component.literal("Steve"), Component.literal("hi"));
        Component shown = ChatHeadComponents.withHead(original, name(), HEAD, "before_name");
        assertSame(original, shown.getSiblings().get(1));
        assertEquals("@<Steve> hi", shown.getString());
        assertNull(ChatHeadComponents.insertBefore(original, name(), HEAD));
        Component noName = ChatHeadComponents.withHead(original, null, HEAD, "before_name");
        assertEquals("@<Steve> hi", noName.getString());
    }

    @Test
    void onlyTheFirstOccurrenceGetsAHead() {
        MutableComponent name = name();
        Component original = Component.translatableWithFallback("custom.whisper", "%s whispers to %s", name, name);
        Component shown = ChatHeadComponents.withHead(original, name, ChatHeadComponents.head(STEVE), "before_name");
        assertEquals(1, ChatHeadComponents.countHeads(shown));
    }

    @Test
    void hoverAndClickContentsAreNotSearched() {
        MutableComponent name = name();
        Component original = Component.literal("x").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(name)));
        assertNull(ChatHeadComponents.insertBefore(original, name, HEAD));
    }
}
