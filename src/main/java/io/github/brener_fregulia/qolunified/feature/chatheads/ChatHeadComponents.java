package io.github.brener_fregulia.qolunified.feature.chatheads;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Pure functions that build display copies of chat components with a head (chat-heads-v1.md CH-2 to CH-6). The
 * input component is never modified; unchanged subtrees are shared, not copied. Text is never read.
 */
public final class ChatHeadComponents {
    private ChatHeadComponents() {
    }

    /**
     * The vanilla player-head glyph (base and hat layer) for {@code sender}. Its skin comes from vanilla's skin cache;
     * the empty fallback keeps the plain text ({@code getString()}, logs) identical to the original message. The glyph
     * is tinted by its text color, so it is fixed to white, upright and not bold: a gray italic whisper must not darken
     * or slant the skin.
     */
    public static Component head(GameProfile sender) {
        Objects.requireNonNull(sender, "sender");
        return Component.object(new PlayerSprite(ResolvableProfile.createResolved(sender), true), Component.empty())
                .withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false).withBold(false));
    }

    /**
     * The display copy of {@code message} with {@code head} placed by {@code position}. In {@code before_name} mode
     * the head goes directly before the first occurrence of {@code name}, found by identity; if it is not there, the
     * head goes before the line (CH-4).
     */
    public static Component withHead(Component message, Component name, Component head, String position) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(head, "head");
        if (ChatHeadsSettings.BEFORE_NAME.equals(position) && name != null) {
            Component inserted = insertBefore(message, name, head);
            if (inserted != null) {
                return inserted;
            }
        }
        return Component.empty().append(head).append(message);
    }

    /**
     * A copy of {@code root} with {@code head} directly before the first occurrence of {@code target} (by identity),
     * or {@code null} if {@code target} is not in the tree. Translation arguments and siblings are searched in
     * display order; hover and click contents are not.
     */
    static Component insertBefore(Component root, Component target, Component head) {
        if (root == target) {
            return Component.empty().append(head).append(target);
        }
        if (root.getContents() instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component arg) {
                    Component replaced = insertBefore(arg, target, head);
                    if (replaced != null) {
                        Object[] copy = args.clone();
                        copy[i] = replaced;
                        var contents = new TranslatableContents(translatable.getKey(), translatable.getFallback(), copy);
                        return rebuild(root, MutableComponent.create(contents), root.getSiblings());
                    }
                }
            }
        }
        List<Component> siblings = root.getSiblings();
        for (int i = 0; i < siblings.size(); i++) {
            Component replaced = insertBefore(siblings.get(i), target, head);
            if (replaced != null) {
                var copy = new ArrayList<>(siblings);
                copy.set(i, replaced);
                return rebuild(root, MutableComponent.create(root.getContents()), copy);
            }
        }
        return null;
    }

    private static Component rebuild(Component original, MutableComponent shell, List<Component> siblings) {
        shell.setStyle(original.getStyle());
        siblings.forEach(shell::append);
        return shell;
    }

    /** Number of player-head glyphs in {@code component}, searched like {@link #insertBefore}. */
    public static int countHeads(Component component) {
        int count = component.getContents() instanceof net.minecraft.network.chat.contents.ObjectContents object
                && object.contents() instanceof PlayerSprite ? 1 : 0;
        if (component.getContents() instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component c) {
                    count += countHeads(c);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            count += countHeads(sibling);
        }
        return count;
    }
}
