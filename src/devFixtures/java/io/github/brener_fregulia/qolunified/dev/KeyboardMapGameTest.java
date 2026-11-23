package io.github.brener_fregulia.qolunified.dev;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.InputId;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.InputState;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyCap;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapHooks;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapScreen;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapTexts;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.RemapSession;
import io.github.brener_fregulia.qolunified.ui.FeatureActions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Development-only client game test for the keyboard map (keymap-v1.md §13). Runs in the disposable
 * {@code build/run-gametest-keyboardmap} game directory, so its {@code options.txt} is disposable too; it never touches
 * a real installation. Input is simulated through Fabric's test input (real mouse clicks and key presses).
 */
final class KeyboardMapGameTest {
    private static final int SCANCODE_W = InputConstants.KEY_W;
    private static final int SCANCODE_Q = InputConstants.KEY_Q;
    private static final int SCANCODE_F7 = InputConstants.KEY_F7;

    private final ClientGameTestContext ctx;

    KeyboardMapGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        check(FabricLoader.getInstance().getGameDir().getFileName().toString().startsWith("run-gametest"),
                "disposable game directory");
        on(core -> check(core.lifecycle().state("keyboard_map") == FeatureState.ACTIVE, "enabled by default"));
        resetAllMappings();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("gamemode survival @a");
            catalogAndOverlaps();
            mouseSelection();
            searchAndFilters();
            remapping();
            noGameplayInput(world);
            scales();
            entryPoints();
        }
        resetAllMappings();
    }

    // KM-AC-1, KM-AC-2, KM-AC-7, KM-AC-8, KM-AC-9
    private void catalogAndOverlaps() {
        KeyboardMapScreen screen = open();
        shot("01-in-world-default");
        ctx.computeOnClient(mc -> {
            var catalog = screen.catalog();
            check(catalog.bindings().size() == mc.options.keyMappings.length, "every mapping listed");
            var ids = new HashSet<String>();
            catalog.bindings().forEach(b -> check(ids.add(b.id()), "listed once: " + b.id()));
            check(catalog.state(InputId.key(SCANCODE_Q)) == InputState.OVERLAP, "Drop and the modded action share Q");
            check(catalog.on(InputId.key(SCANCODE_Q)).stream().anyMatch(b -> b.id().equals(DevKeyMappings.ON_Q)),
                    "modded mapping takes part");
            check(catalog.state(InputId.key(SCANCODE_W)) == InputState.SINGLE, "W has one action");
            check(catalog.state(InputId.mouse(1)) == InputState.SINGLE, "left mouse button: Attack");
            check(catalog.state(InputId.key(1)) == InputState.UNBOUND, "nothing on an unknown scancode");
            check(catalog.binding(DevKeyMappings.UNBOUND).orElseThrow().unbound(), "unbound modded action");
            check(screen.drawnSections().contains(KeyCap.Section.MAIN), "main block drawn");
            return null;
        });
    }

    // KM-AC-4: clicking a key highlights its rows; selecting a row outlines its key.
    private void mouseSelection() {
        KeyboardMapScreen screen = current();
        click(ctx.computeOnClient(mc -> screen.keyCenter(SCANCODE_Q)));
        ctx.computeOnClient(mc -> {
            check(InputId.key(SCANCODE_Q).equals(screen.selection().highlightedInput(screen.catalog())), "Q selected");
            check(screen.selection().highlightedBindings(screen.catalog()).size() == 2, "both Q rows highlighted");
            return null;
        });
        shot("02-key-q-selected-labels");
        ctx.runOnClient(mc -> screen.selectBinding("key.forward"));
        ctx.computeOnClient(mc -> {
            check(InputId.key(SCANCODE_W).equals(screen.selection().highlightedInput(screen.catalog())),
                    "row selects its key");
            return null;
        });
        shot("03-row-forward-selected");
    }

    // KM-AC-5, KM-AC-6
    private void searchAndFilters() {
        KeyboardMapScreen screen = current();
        int[] box = ctx.computeOnClient(mc -> new int[] {screen.geometry().margin() + 10,
                screen.geometry().toolbarY() + 10});
        click(box);
        ctx.getInput().typeChars("key:q");
        ctx.waitTicks(2);
        ctx.computeOnClient(mc -> {
            check(screen.visibleRowIds().size() == 2 && screen.visibleRowIds().contains("key.drop"),
                    "key:q finds both Q actions: " + screen.visibleRowIds());
            return null;
        });
        shot("04-search-key-q");
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
        ctx.waitTicks(2);
        ctx.computeOnClient(mc -> {
            check(mc.gui.screen() == screen, "first Escape clears the search, the screen stays");
            check(screen.visibleRowIds().size() == screen.catalog().bindings().size(), "all rows back");
            return null;
        });
        ctx.getInput().typeChars("sample");
        ctx.waitTicks(2);
        ctx.computeOnClient(mc -> {
            check(screen.visibleRowIds().contains(DevKeyMappings.UNBOUND), "unbound action found by search");
            return null;
        });
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
        // Category and visibility: the second and third toolbar buttons.
        AbstractWidget category = button(screen, 1);
        AbstractWidget show = button(screen, 2);
        pressButton(category);
        String first = ctx.computeOnClient(mc -> screen.catalog().categories().getFirst());
        ctx.computeOnClient(mc -> {
            check(screen.visibleRowIds().stream().allMatch(id ->
                    screen.catalog().binding(id).orElseThrow().category().equals(first)), "category filter");
            return null;
        });
        shot("05-category-" + "first");
        pressButton(category);
        for (int i = 0; i < screen.catalog().categories().size() - 1; i++) {
            pressButton(category);
        }
        pressButton(show);
        ctx.computeOnClient(mc -> {
            check(screen.visibleRowIds().stream().allMatch(id -> screen.catalog().state(
                    screen.catalog().binding(id).orElseThrow()) == InputState.OVERLAP), "overlaps only");
            check(!screen.visibleRowIds().isEmpty(), "Q overlap shown");
            return null;
        });
        shot("06-filter-overlaps");
        pressButton(show);
        pressButton(show);
        ctx.computeOnClient(mc -> {
            check(screen.visibleRowIds().contains(DevKeyMappings.UNBOUND), "unbound filter");
            check(screen.visibleRowIds().stream().allMatch(id -> screen.catalog().binding(id).orElseThrow().unbound()),
                    "unbound only");
            return null;
        });
        shot("07-filter-unbound");
        pressButton(show);
    }

    // KM-AC-R1..R3 and the overlap confirmation, through real clicks and key presses.
    private void remapping() {
        KeyboardMapScreen screen = current();
        ctx.runOnClient(mc -> screen.selectBinding(DevKeyMappings.ON_Q));
        Button change = bottomButton(screen, KeyboardMapTexts.CHANGE);

        pressButton(change);
        check(state(screen) == RemapSession.State.CAPTURING, "Change starts capturing");
        shot("08-capturing");
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(SCANCODE_F7));
        ctx.waitTicks(2);
        check(state(screen) == RemapSession.State.IDLE, "one input captured");
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.f7"), "remapped to F7");
        check(optionsFileContains("key_" + DevKeyMappings.ON_Q + ":key.keyboard.f7"), "saved by vanilla Options");
        check(ctx.computeOnClient(mc -> KeyMapping.get(DevKeyMappings.ON_Q) != null
                && screen.catalog().state(InputId.key(SCANCODE_Q)) == InputState.SINGLE), "lookup rebuilt");

        // Escape cancels a capture without changes.
        pressButton(change);
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
        ctx.waitTicks(2);
        check(state(screen) == RemapSession.State.IDLE, "Escape cancels capture");
        check(ctx.computeOnClient(mc -> mc.gui.screen() == screen), "Escape during capture does not close");
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.f7"), "unchanged after cancel");

        // A new overlap waits for confirmation; Cancel keeps the old key, Confirm keeps the player's choice.
        pressButton(change);
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(SCANCODE_W));
        ctx.waitTicks(2);
        check(state(screen) == RemapSession.State.CONFIRMING, "overlap with Walk Forwards needs confirmation");
        shot("09-confirm-overlap");
        pressButton(bottomButton(screen, KeyboardMapTexts.CANCEL));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.f7"), "Cancel keeps F7");
        pressButton(change);
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(SCANCODE_W));
        ctx.waitTicks(2);
        pressButton(bottomButton(screen, KeyboardMapTexts.CONFIRM));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.w"), "Confirm keeps the overlap");
        check(ctx.computeOnClient(mc -> screen.catalog().state(InputId.key(SCANCODE_W)) == InputState.OVERLAP),
                "W is now an overlap");

        // Mouse capture.
        pressButton(change);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_5);
        ctx.waitTicks(2);
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.mouse.5"), "mouse button 5 captured");

        // Reset and Clear.
        pressButton(bottomButton(screen, KeyboardMapTexts.RESET));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.q"), "Reset restores the declared default");
        pressButton(bottomButton(screen, KeyboardMapTexts.CLEAR));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.unknown"), "Clear unbinds");
        check(optionsFileContains("key_" + DevKeyMappings.ON_Q + ":key.keyboard.unknown"), "clear saved");
        pressButton(bottomButton(screen, KeyboardMapTexts.RESET));
        shot("10-after-remapping");
    }

    // KM-AC-12: keys bound to gameplay do nothing while the screen is open.
    private void noGameplayInput(TestSingleplayerContext world) {
        world.getServer().runCommand("give @a minecraft:dirt 8");
        ctx.waitTicks(5);
        var before = ctx.computeOnClient(mc -> new Object[] {mc.player.position(), mc.player.getInventory()
                .getSelectedSlot(), mc.player.getInventory().getSelectedItem().getCount()});
        ctx.getInput().holdKey(InputConstants.Type.KEYBOARD.getOrCreate(SCANCODE_W));
        ctx.waitTicks(20);
        ctx.getInput().releaseKey(InputConstants.Type.KEYBOARD.getOrCreate(SCANCODE_W));
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(SCANCODE_Q));
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_3));
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_SPACE));
        ctx.waitTicks(10);
        ctx.computeOnClient(mc -> {
            check(mc.player.position().distanceTo((net.minecraft.world.phys.Vec3) before[0]) < 0.01, "did not move");
            check(mc.player.getInventory().getSelectedSlot() == (int) before[1], "hotbar slot unchanged");
            ItemStack held = mc.player.getInventory().getSelectedItem();
            check(held.getCount() == (int) before[2], "nothing dropped");
            check(mc.gui.screen() instanceof KeyboardMapScreen, "screen still open");
            return null;
        });
    }

    // KM-AC-13: GUI scales and window sizes; screenshots for review.
    private void scales() {
        int[][] windows = {{854, 480}, {1280, 720}, {1920, 1080}};
        for (int[] window : windows) {
            ctx.getInput().resizeWindow(window[0], window[1]);
            for (int scale = 1; scale <= 4; scale++) {
                int s = scale;
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(s);
                    mc.resizeGui();
                });
                ctx.waitTicks(2);
                KeyboardMapScreen screen = current();
                int[] info = ctx.computeOnClient(mc -> new int[] {screen.geometry().unit(),
                        screen.geometry().split() ? 1 : 0, screen.geometry().tabbed() ? 1 : 0, screen.width,
                        screen.height});
                check(info[0] >= 3, "keys at least 12 px at " + window[0] + "x" + window[1] + " scale " + scale);
                ctx.takeScreenshot("keyboardmap-11-" + window[0] + "x" + window[1] + "-gui" + scale + "-" + info[3]
                        + "x" + info[4] + (info[1] == 1 ? "-split" : "") + (info[2] == 1 ? "-tabs" : ""));
            }
        }
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(0);
            mc.resizeGui();
        });
        ctx.getInput().resizeWindow(854, 480);
        ctx.waitTicks(2);
        // ABNT2 drawing, through the toolbar layout button (fourth toolbar button).
        KeyboardMapScreen screen = current();
        pressButton(button(screen, 3));
        on(core -> check(core.choices().get("keyboard_map").settings().getEnum("layout").equals("abnt2"),
                "layout persisted through the Core"));
        KeyboardMapScreen abnt = current();
        ctx.computeOnClient(mc -> {
            check(abnt.keyCenter(100) != null || abnt.geometry().split(), "key left of Z drawn on ABNT2");
            return null;
        });
        ctx.getInput().resizeWindow(1600, 900);
        ctx.waitTicks(3);
        shot("12-abnt2-1600x900");
        pressButton(button(current(), 3));
        ctx.getInput().resizeWindow(854, 480);
        ctx.waitTicks(3);
    }

    // EP-1..EP-3 and LIVE toggle (KM-AC-15).
    private void entryPoints() {
        ctx.setScreen(() -> new ControlsScreen(null, Minecraft.getInstance().options));
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> controlsButton(mc) != null), "Controls has a Keyboard Map button");
        shot("13-controls-entry");
        ctx.runOnClient(mc -> controlsButton(mc).onPress(null));
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof KeyboardMapScreen), "button opens it");
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ControlsScreen), "Escape returns to Controls");
        check(FeatureActions.find("keyboard_map").isPresent(), "settings action registered");
        if (FabricLoader.getInstance().isModLoaded("controlling")) {
            // With Controlling: its Key Binds screen opens from ours, and ours still opens from Controls.
            ctx.runOnClient(mc -> controlsButton(mc).onPress(null));
            ctx.waitTicks(2);
            pressButton(bottomButton(current(), KeyboardMapTexts.KEY_BINDS));
            ctx.waitTicks(3);
            shot("14-controlling-key-binds");
            // Controlling focuses its search box; its first Escape closes the suggestions.
            for (int i = 0; i < 3 && !ctx.computeOnClient(mc -> mc.gui.screen() instanceof KeyboardMapScreen); i++) {
                ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
                ctx.waitTicks(3);
            }
            check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof KeyboardMapScreen),
                    "Escape from Key Binds returns to the keyboard map");
            ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
            ctx.waitTicks(2);
        }

        setEnabled(false);
        ctx.setScreen(() -> new ControlsScreen(null, Minecraft.getInstance().options));
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> controlsButton(mc) == null), "no Controls button while disabled");
        ctx.setScreen(() -> null);
        ctx.runOnClient(mc -> KeyMapping.get(KeyboardMapTexts.KEY_OPEN).setKey(
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8)));
        ctx.runOnClient(mc -> KeyMapping.resetMapping());
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8));
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "open key does nothing while disabled");
        setEnabled(true);
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8));
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof KeyboardMapScreen), "open key works when enabled");
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "Escape returns to the game");
    }

    private static Button controlsButton(Minecraft mc) {
        return findButton(mc.gui.screen(), Component.translatable(KeyboardMapTexts.OPEN));
    }

    /** Depth-first search through containers (the options list and its rows). */
    private static Button findButton(net.minecraft.client.gui.components.events.GuiEventListener node,
            Component message) {
        if (node instanceof Button b && b.getMessage().equals(message)) {
            return b;
        }
        if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
            for (var child : container.children()) {
                Button found = findButton(child, message);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ---- helpers ----

    private KeyboardMapScreen open() {
        ctx.runOnClient(mc -> KeyboardMapHooks.open(mc, null));
        ctx.waitTicks(3);
        return current();
    }

    private KeyboardMapScreen current() {
        return ctx.computeOnClient(mc -> (KeyboardMapScreen) mc.gui.screen());
    }

    private RemapSession.State state(KeyboardMapScreen screen) {
        return ctx.computeOnClient(mc -> screen.session().state());
    }

    private String mappingKey(String id) {
        return ctx.computeOnClient(mc -> KeyMapping.get(id).saveString());
    }

    private boolean optionsFileContains(String line) {
        Path options = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        try {
            return Files.readAllLines(options).contains(line);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + options, e);
        }
    }

    /** Clicks at a GUI position with the real mouse. */
    private void click(int[] gui) {
        check(gui != null, "position on screen");
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(gui[0] * scale + scale / 2, gui[1] * scale + scale / 2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    private void pressButton(AbstractWidget button) {
        check(ctx.computeOnClient(mc -> button.visible && button.active), "button usable: " + button.getMessage()
                .getString());
        click(ctx.computeOnClient(mc -> new int[] {button.getX() + button.getWidth() / 2,
                button.getY() + button.getHeight() / 2}));
    }

    /** The {@code index}-th button of the toolbar (0 is the search box). */
    private AbstractWidget button(KeyboardMapScreen screen, int index) {
        return ctx.computeOnClient(mc -> {
            int y = screen.geometry().toolbarY();
            var row = screen.children().stream().filter(c -> c instanceof AbstractWidget w && w.getY() == y)
                    .map(c -> (AbstractWidget) c).sorted(java.util.Comparator.comparingInt(AbstractWidget::getX))
                    .toList();
            return row.get(index);
        });
    }

    private Button bottomButton(KeyboardMapScreen screen, String key) {
        return ctx.computeOnClient(mc -> screen.children().stream()
                .filter(c -> c instanceof Button b && b.getMessage().equals(Component.translatable(key)))
                .map(c -> (Button) c).findFirst().orElseThrow());
    }

    private void resetAllMappings() {
        ctx.runOnClient(mc -> {
            for (KeyMapping mapping : mc.options.keyMappings) {
                mapping.setKey(mapping.getDefaultKey());
            }
            KeyMapping.resetMapping();
            mc.options.save();
        });
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of("keyboard_map", core.choices().get("keyboard_map").withEnabled(enabled))));
        ctx.waitTicks(2);
    }

    private void on(Consumer<CoreRuntime> action) {
        ctx.runOnClient(mc -> action.accept(QolUnifiedClient.core()));
    }

    private void shot(String name) {
        ctx.waitTicks(3);
        ctx.takeScreenshot("keyboardmap-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
