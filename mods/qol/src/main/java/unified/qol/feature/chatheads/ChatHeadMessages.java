package unified.qol.feature.chatheads;

import com.mojang.authlib.GameProfile;
import unified.qol.mixin.ChatComponentAccessor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Display copies of player chat (C-1) and the refresh of lines already in chat (C-2). Client thread only.
 *
 * <p>For each player message shown, the original decorated component, the bound name and the sender profile are
 * remembered, for as many messages as vanilla keeps in chat. Every display copy is built from that original, never
 * from another copy, so a message has at most one head and switching back restores the original component itself.
 * Nothing else is stored: signatures, tags and timestamps stay in vanilla's {@link GuiMessage}.
 */
public final class ChatHeadMessages {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");
    /** Vanilla's {@code ChatComponent.MAX_CHAT_HISTORY} in 26.3. */
    static final int CAPACITY = 100;

    private static final Deque<Source> SOURCES = new ArrayDeque<>();
    private static String applied;

    private ChatHeadMessages() {
    }

    /**
     * C-1: records a player message and returns what to display for the current position. Returns {@code message}
     * itself while the feature is inactive, or if building the copy fails.
     */
    public static Component display(Component message, Component name, GameProfile sender) {
        if (message == null || sender == null) {
            return message;
        }
        try {
            Source source = new Source(message, name, sender);
            SOURCES.addFirst(source);
            while (SOURCES.size() > CAPACITY) {
                SOURCES.removeLast();
            }
            return source.display(ChatHeadsFeature.position());
        } catch (RuntimeException e) {
            LOGGER.warn("Feature {}: could not add a head to a chat message; showing it unchanged", ChatHeadsSettings.ID, e);
            return message;
        }
    }

    /** C-2: brings lines already in chat to the current position, once per change. Called every client tick. */
    static void refreshIfChanged(ChatComponent chat) {
        String position = ChatHeadsFeature.position();
        if (Objects.equals(position, applied)) {
            return;
        }
        applied = position;
        try {
            if (refresh(((ChatComponentAccessor) chat).qolUnified$allMessages(), position)) {
                chat.rescaleChat();
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Feature {}: could not update messages already in chat", ChatHeadsSettings.ID, e);
        }
    }

    /**
     * Replaces the content of every remembered message in {@code messages} with its display copy for
     * {@code position}, keeping order, time, signature, source and tag. Messages that are not remembered (system
     * messages, deleted markers) are left alone. Returns whether anything changed.
     */
    static boolean refresh(List<GuiMessage> messages, String position) {
        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            GuiMessage message = messages.get(i);
            Source source = find(message.content());
            if (source == null) {
                continue;
            }
            Component wanted = source.display(position);
            if (wanted != message.content()) {
                messages.set(i, new GuiMessage(message.addedTime(), wanted, message.signature(), message.source(),
                        message.tag()));
                changed = true;
            }
        }
        return changed;
    }

    private static Source find(Component content) {
        for (Source source : SOURCES) {
            if (source.owns(content)) {
                return source;
            }
        }
        return null;
    }

    /** Forgets every remembered message (tests only). */
    static void clear() {
        SOURCES.clear();
        applied = null;
    }

    private static final class Source {
        private final Component original;
        private final Component name;
        private final GameProfile sender;
        private Component head;
        private Component beforeName;
        private Component beforeLine;

        Source(Component original, Component name, GameProfile sender) {
            this.original = original;
            this.name = name;
            this.sender = sender;
        }

        boolean owns(Component content) {
            return content == original || content == beforeName || content == beforeLine;
        }

        Component display(String position) {
            if (position == null) {
                return original;
            }
            if (head == null) {
                head = ChatHeadComponents.head(sender);
            }
            if (ChatHeadsSettings.BEFORE_LINE.equals(position)) {
                if (beforeLine == null) {
                    beforeLine = ChatHeadComponents.withHead(original, name, head, position);
                }
                return beforeLine;
            }
            if (beforeName == null) {
                beforeName = ChatHeadComponents.withHead(original, name, head, position);
            }
            return beforeName;
        }
    }
}
