package unified.qol.dev;

import unified.qol.QolUnifiedClient;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureState;
import unified.qol.core.config.FeatureChoice;
import unified.qol.feature.chatheads.ChatHeadComponents;
import unified.qol.mixin.ChatComponentAccessor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.LoggedChatEvent;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;

/**
 * Development-only client game test for Chat Heads (chat-heads-v1.md CH-AC). Uses a disposable singleplayer world in
 * {@code build/run-gametest-chatheads}; it never touches a real installation, world or server. Assertions read the
 * chat's own messages; screenshots are saved for visual review.
 */
final class ChatHeadsGameTest {
    private final ClientGameTestContext ctx;
    private TestServerContext server;
    private String name;

    ChatHeadsGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        on(core -> {
            check(core.lifecycle().state("chat_heads") == FeatureState.ACTIVE, "enabled by default");
            check(core.lifecycle().state("food_hud") == FeatureState.ACTIVE, "Food HUD still enabled");
        });
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamemode survival @a");
            singleplayer();
        }
    }

    private void singleplayer() {
        UUID self = ctx.computeOnClient(mc -> mc.player.getUUID());
        name = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());

        // CH-AC-1 / CH-AC-6: player chat gets exactly one head from the sender profile, directly before the name.
        GuiMessage hello = chat("Hello from chat heads");
        check(heads(hello) == 1, "one head");
        check(self.equals(headProfile(hello.content())), "head from the sender profile");
        check(hello.content().getString().equals(ctx.computeOnClient(mc -> "<" + mc.player.getGameProfile().name()
                + "> Hello from chat heads")), "text unchanged, no space added: " + hello.content().getString());
        check(hello.source() == GuiMessageSource.PLAYER, "player source kept");
        // CH-AC-3: the dev profile has no textures, so the glyph shows vanilla's default skin.
        shot("01-before-name-default-skin");

        // CH-AC-4 / CH-AC-12: the report log keeps the original message; the trust tag matches the feature off.
        check(loggedHeads() == 0, "report log holds the original message");
        setEnabled(false);
        GuiMessage plain = chat("Hello without heads");
        check(heads(plain) == 0, "no head while disabled");
        check(java.util.Objects.equals(plain.tag(), hello.tag()), "same trust tag on and off");
        check(java.util.Objects.equals(plain.signature() == null, hello.signature() == null), "signature kept");

        // CH-AC-9 (C-2): enabling updates lines already in chat; nothing is duplicated or reordered.
        List<GuiMessage> before = messages();
        setEnabled(true);
        List<GuiMessage> after = messages();
        check(after.size() == before.size(), "same number of messages");
        for (int i = 0; i < after.size(); i++) {
            check(after.get(i).addedTime() == before.get(i).addedTime(), "order and time kept");
            check(after.get(i).signature() == before.get(i).signature(), "signature object kept");
            check(after.get(i).tag() == before.get(i).tag(), "tag kept");
            check(after.get(i).content().getString().equals(before.get(i).content().getString()), "text kept");
            check(heads(after.get(i)) == expectedHeads(after.get(i)), "one head per player line, none on others");
        }
        shot("02-refreshed-after-enable");

        // CH-AC-5: system messages, command output and lookalike text never get a head.
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("help"));
        ctx.waitTicks(10);
        server.runCommand("tellraw @a \"<" + name + "> fake player line\"");
        server.runCommand("say from the server console");
        ctx.waitTicks(10);
        for (GuiMessage message : messages()) {
            check(heads(message) == expectedHeads(message), "head count on " + message.content().getString());
        }
        // Console /say arrives as disguised chat: player source, but no sender profile, so no head.
        check(messages().stream().anyMatch(m -> m.source() == GuiMessageSource.PLAYER
                && m.content().getString().startsWith("[Server]") && heads(m) == 0), "disguised chat has no head");
        shot("03-system-messages-no-head");

        // CH-AC-6 / CH-AC-9: Before Line, live, for new and existing lines.
        setPosition("before_line");
        for (GuiMessage message : messages()) {
            check(heads(message) == expectedHeads(message), "existing lines moved, never stacked: "
                    + message.content().getString());
        }
        GuiMessage line = chat("Before line message");
        check(line.content().getSiblings().size() == 2 && heads(line) == 1, "head before the line");
        shot("04-before-line");
        setPosition("before_name");

        // Styled chat types: whispers are gray italic, team chat is colored; the head must not inherit that style.
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("msg " + name + " styled whisper"));
        server.runCommand("team add blue");
        server.runCommand("team modify blue color blue");
        server.runCommand("team join blue @a");
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("teammsg styled team message"));
        ctx.waitTicks(10);
        shot("04b-styled-chat-types");
        server.runCommand("team remove blue");

        // CH-AC-7: a long message wraps; the head stays on the first line.
        chat("This is a long chat message that keeps going so that it has to wrap onto several lines in the chat"
                + " window at the default chat width, to check that the head stays on the first line.");
        shot("05-long-message-wrap");

        // CH-AC-8: chat scale and GUI scale; the head is text, so it scales with it.
        for (double scale : new double[] {0.5, 1.0}) {
            ctx.runOnClient(mc -> {
                mc.options.chatScale().set(scale);
                mc.gui.hud.getChat().rescaleChat();
            });
            chat("Chat scale " + scale);
            shot("06-chat-scale-" + scale);
        }
        for (int gui : new int[] {1, 2, 3, 4}) {
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(gui);
                mc.resizeGui();
            });
            chat("GUI scale " + gui);
            shot("07-gui-scale-" + gui);
        }
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(0);
            mc.resizeGui();
        });

        // Scrolling: open chat and scroll up; heads move with their lines.
        for (int i = 0; i < 15; i++) {
            chat("Scroll line " + i);
        }
        ctx.getInput().pressKey(o -> o.keyChat);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.gui.hud.getChat().scrollChat(6));
        shot("08-scrolled");
        ctx.setScreen(() -> null);

        // CH-AC-16 (TAB) is checked manually on a server: singleplayer shows no player list for one player.
        setEnabled(false);
        check(messages().stream().allMatch(m -> heads(m) == 0), "disabling removes every head");
        shot("11-disabled-existing-lines");
        setEnabled(true);
        on(core -> check(core.lifecycle().state("food_hud") == FeatureState.ACTIVE, "Food HUD unaffected"));
    }

    /**
     * CH-AC-15 / CH-AC-17, with the original Chat Heads loaded: this feature starts disabled by the conflict, and the
     * same messages are captured with the reference drawing, for side-by-side review with {@link #run()}. An explicit
     * enable then wins over the conflict default.
     */
    void compareWithReference() {
        check(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("chat_heads"), "Chat Heads loaded");
        on(core -> {
            check(core.lifecycle().state("chat_heads") == FeatureState.INACTIVE, "conflict default disables");
            check(core.compatibility("chat_heads").defaultSuppressed(), "suppressed by Chat Heads");
        });
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            name = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());
            GuiMessage hello = chat("Hello from chat heads");
            // The reference may insert its own glyph; record what it produced, for comparison only.
            int referenceHeads = heads(hello);
            org.slf4j.LoggerFactory.getLogger("qol_unified_dev").info(
                    "Chat Heads comparison: reference message has {} PlayerSprite glyph(s): {}", referenceHeads,
                    hello.content());
            server.runCommand("tellraw @a \"<" + name + "> fake player line\"");
            chat("This is a long chat message that keeps going so that it has to wrap onto several lines in the chat"
                    + " window at the default chat width, to check that the head stays on the first line.");
            for (int gui : new int[] {1, 2}) {
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(gui);
                    mc.resizeGui();
                });
                chat("GUI scale " + gui);
                shot("ref-gui-scale-" + gui);
            }
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(0);
                mc.resizeGui();
            });
            setEnabled(true);
            on(core -> check(core.choices().get("chat_heads").explicitEnabled(), "explicit choice recorded"));
            GuiMessage both = chat("Both mods enabled");
            check(heads(both) == referenceHeads + 1, "this feature adds exactly one head on top of the reference");
            shot("ref-both-enabled");
        }
    }

    /** Sends chat as the local player and returns its line once displayed. */
    private GuiMessage chat(String text) {
        ctx.runOnClient(mc -> mc.player.connection.sendChat(text));
        ctx.waitFor(mc -> latestWith(mc, text) != null, 100);
        return ctx.computeOnClient(mc -> latestWith(mc, text));
    }

    private static GuiMessage latestWith(Minecraft mc, String text) {
        for (GuiMessage message : ((ChatComponentAccessor) mc.gui.hud.getChat()).qolUnified$allMessages()) {
            if (message.content().getString().endsWith("> " + text)) {
                return message;
            }
        }
        return null;
    }

    private List<GuiMessage> messages() {
        return ctx.computeOnClient(mc -> List.copyOf(((ChatComponentAccessor) mc.gui.hud.getChat())
                .qolUnified$allMessages()));
    }

    /** One head on chat sent by the local player (with a sender profile), none on anything else. */
    private int expectedHeads(GuiMessage message) {
        return message.source() == GuiMessageSource.PLAYER && message.content().getString().startsWith("<" + name + "> ")
                ? 1 : 0;
    }

    private static int heads(GuiMessage message) {
        return ChatHeadComponents.countHeads(message.content());
    }

    private static UUID headProfile(Component component) {
        if (component.getContents() instanceof ObjectContents object && object.contents() instanceof PlayerSprite s) {
            return s.player().partialProfile().id();
        }
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component c && headProfile(c) != null) {
                    return headProfile(c);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            UUID id = headProfile(sibling);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** Heads found in the player messages of vanilla's report log (should be none). */
    private int loggedHeads() {
        return ctx.computeOnClient(mc -> {
            ChatLog log = mc.getReportingContext().chatLog();
            int heads = 0;
            int players = 0;
            for (int i = log.start(); i <= log.end(); i++) {
                LoggedChatEvent event = log.lookup(i);
                if (event instanceof LoggedChatMessage.Player player) {
                    players++;
                    heads += ChatHeadComponents.countHeads(player.message().decoratedContent());
                    heads += ChatHeadComponents.countHeads(player.toContentComponent());
                }
            }
            check(players > 0, "player messages logged for reporting");
            return heads;
        });
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of("chat_heads", core.choices().get("chat_heads").withEnabled(enabled))));
        on(core -> check(core.lifecycle().state("chat_heads")
                == (enabled ? FeatureState.ACTIVE : FeatureState.INACTIVE), "state after toggle"));
        ctx.waitTicks(2);
    }

    private void setPosition(String position) {
        on(core -> {
            FeatureChoice choice = core.choices().get("chat_heads");
            core.saveAndApply(Map.of("chat_heads", choice.withSettings(choice.settings().with("position", position))));
        });
        ctx.waitTicks(2);
    }

    private void on(java.util.function.Consumer<CoreRuntime> assertion) {
        ctx.runOnClient(mc -> assertion.accept(QolUnifiedClient.core()));
    }

    private void shot(String name) {
        ctx.waitTicks(3);
        ctx.takeScreenshot("chatheads-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
