package io.github.brener_fregulia.qolunified.feature.chatheads;

import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureContext;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import java.util.Objects;

/**
 * Chat Heads (chat-heads-v1.md). The chat Mixins are always applied and the refresh hook is installed once, on the
 * first enable; both only act on the current {@link #position()}, so enabling and disabling is LIVE and cannot fail.
 */
public final class ChatHeadsFeature implements Feature {
    private static volatile FeatureContext active;

    private final Runnable installHooks;
    private boolean hooksInstalled;

    public ChatHeadsFeature() {
        // Resolved only on the first enable, so loading the registry does not load chat classes.
        this(() -> ChatHeadsHooks.install());
    }

    ChatHeadsFeature(Runnable installHooks) {
        this.installHooks = Objects.requireNonNull(installHooks, "installHooks");
    }

    @Override
    public FeatureDescriptor descriptor() {
        return ChatHeadsSettings.DESCRIPTOR;
    }

    @Override
    public void enable(FeatureContext context) {
        Objects.requireNonNull(context, "context");
        if (!hooksInstalled) {
            installHooks.run();
            hooksInstalled = true;
        }
        active = context;
    }

    @Override
    public void disable() {
        active = null;
    }

    /** The current head position ({@code before_name} or {@code before_line}), or {@code null} while inactive. */
    public static String position() {
        FeatureContext context = active;
        return context == null ? null : context.settings().getEnum(ChatHeadsSettings.POSITION);
    }
}
