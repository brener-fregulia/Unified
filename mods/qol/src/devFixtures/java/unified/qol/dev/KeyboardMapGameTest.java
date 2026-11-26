package unified.qol.dev;

import com.mojang.blaze3d.platform.InputConstants;
import unified.qol.QolUnifiedClient;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureState;
import unified.qol.feature.keyboardmap.InputId;
import unified.qol.feature.keyboardmap.InputState;
import unified.qol.feature.keyboardmap.KeyCap;
import unified.qol.feature.keyboardmap.KeyboardMapHooks;
import unified.qol.feature.keyboardmap.KeyboardMapScreen;
import unified.qol.feature.keyboardmap.KeyboardMapTexts;
import unified.qol.feature.keyboardmap.RemapSession;
import unified.qol.feature.keyboardmap.Selection;
import unified.qol.ui.FeatureActions;
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
            // Main flow at 1600×900, GUI scale 2 (800×450): whole keyboard with the mouse beside it.
            ctx.getInput().resizeWindow(1600, 900);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
            });
            ctx.waitTicks(3);
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

    // Explore mode, catalog and states (KM-AC-1, KM-AC-2, KM-AC-7, KM-AC-9; KMUX-AC-1).
    private void catalogAndOverlaps() {
        KeyboardMapScreen screen = open();
        shot("01-explore");
        ctx.computeOnClient(mc -> {
            var catalog = screen.catalog();
            check(catalog.bindings().size() == mc.options.keyMappings.length, "every mapping listed");
            var ids = new HashSet<String>();
            catalog.bindings().forEach(b -> check(ids.add(b.id()), "listed once: " + b.id()));
            check(catalog.state(InputId.key(SCANCODE_Q)) == InputState.OVERLAP, "Drop and the modded action share Q");
            check(catalog.state(InputId.key(SCANCODE_W)) == InputState.SINGLE, "W has one action");
            check(catalog.state(InputId.mouse(1)) == InputState.SINGLE, "left mouse button: Attack");
            check(catalog.binding(DevKeyMappings.UNBOUND).orElseThrow().unbound(), "unbound modded action");
            check(screen.selection().mode() == Selection.Mode.EXPLORE, "opens in Explore");
            check(screen.shownRowButtonKeys().isEmpty(), "no Change, Clear or Reset in Explore");
            check(screen.drawnSections().contains(KeyCap.Section.MAIN), "main block drawn");
            for (int button = 1; button <= 5; button++) {
                check(screen.inputCenter(InputId.mouse(button)) != null, "mouse region " + button + " drawn");
            }
            return null;
        });
    }

    // Selected-input mode for a key, a mouse button and a free key (KMUX-AC-2, KMUX-AC-3, KMUX-AC-5).
    private void mouseSelection() {
        KeyboardMapScreen screen = current();
        click(ctx.computeOnClient(mc -> screen.inputCenter(InputId.key(SCANCODE_Q))));
        ctx.computeOnClient(mc -> {
            check(screen.selection().mode() == Selection.Mode.INPUT, "card opened");
            check(screen.cardBindingIds().size() == 2 && screen.cardBindingIds().contains(DevKeyMappings.ON_Q),
                    "only the two Q actions: " + screen.cardBindingIds());
            check(screen.rowButton("key.drop", KeyboardMapTexts.CHANGE) != null, "per-action Change");
            return null;
        });
        shot("02-selected-key-q-shared");
        click(ctx.computeOnClient(mc -> screen.inputCenter(InputId.key(SCANCODE_Q))));
        check(ctx.computeOnClient(mc -> screen.selection().mode() == Selection.Mode.EXPLORE),
                "clicking Q again returns to Explore");

        click(ctx.computeOnClient(mc -> screen.inputCenter(InputId.mouse(1))));
        ctx.computeOnClient(mc -> {
            check(InputId.mouse(1).equals(screen.selection().highlightedInput(screen.catalog())), "left button");
            check(screen.cardBindingIds().equals(java.util.List.of("key.attack")), "Attack on the left button");
            return null;
        });
        shot("03-selected-mouse-left");
        hover(ctx.computeOnClient(mc -> screen.inputCenter(InputId.mouse(2))));
        shot("04-hover-wheel-note");
        click(ctx.computeOnClient(mc -> screen.inputCenter(InputId.mouse(4))));
        check(ctx.computeOnClient(mc -> screen.selection().mode() == Selection.Mode.INPUT
                && screen.cardBindingIds().isEmpty()), "mouse 4 is free: empty card");
        shot("05-selected-mouse-4-free");
        click(ctx.computeOnClient(mc -> screen.inputCenter(InputId.key(InputConstants.KEY_F7))));
        check(ctx.computeOnClient(mc -> screen.cardBindingIds().isEmpty()), "F7 free: empty card");
        pressButton(bottomButton(screen, KeyboardMapTexts.FIND_UNBOUND));
        ctx.computeOnClient(mc -> {
            check(screen.selection().mode() == Selection.Mode.EXPLORE, "back to Explore");
            check(screen.visibleRowIds().contains(DevKeyMappings.UNBOUND), "unbound filter applied");
            check(screen.visibleRowIds().stream().allMatch(id -> screen.catalog().binding(id).orElseThrow().unbound()),
                    "only unbound actions");
            return null;
        });
        shot("06-unbound-actions");
        // An unbound action opens its own card with Change enabled.
        ctx.runOnClient(mc -> screen.selectBinding(DevKeyMappings.UNBOUND));
        ctx.computeOnClient(mc -> {
            check(screen.selection().mode() == Selection.Mode.BINDING, "binding card");
            Button change = screen.rowButton(DevKeyMappings.UNBOUND, KeyboardMapTexts.CHANGE);
            check(change != null && change.active, "Change enabled for an unbound action");
            check(!screen.rowButton(DevKeyMappings.UNBOUND, KeyboardMapTexts.CLEAR).active, "nothing to clear");
            return null;
        });
        shot("07-unbound-action-card");
        // Row in Explore → its input's card with that action in focus.
        ctx.runOnClient(mc -> screen.selectBinding("key.forward"));
        check(ctx.computeOnClient(mc -> InputId.key(SCANCODE_W).equals(
                screen.selection().highlightedInput(screen.catalog()))), "row selects its key");
        pressKey(InputConstants.KEY_ESCAPE);
        check(ctx.computeOnClient(mc -> screen.selection().mode() == Selection.Mode.EXPLORE
                && mc.gui.screen() == screen), "Escape returns to Explore without closing");
    }

    // Search, filters, search highlighting and search leaving a card (KMUX-AC-11; KM-AC-5, KM-AC-6).
    private void searchAndFilters() {
        KeyboardMapScreen screen = current();
        pressButton(toolbar(screen, 1)); // Filters
        AbstractWidget category = popup(screen, 0);
        // Cycle the visibility filter to "shared" (it may still be on "unbound" from the previous step).
        for (int i = 0; i < 4 && !ctx.computeOnClient(mc -> !screen.visibleRowIds().isEmpty()
                && screen.visibleRowIds().stream().allMatch(id -> screen.catalog().state(
                        screen.catalog().binding(id).orElseThrow()) == InputState.OVERLAP)); i++) {
            pressButton(popup(screen, 1));
        }
        ctx.computeOnClient(mc -> {
            check(!screen.visibleRowIds().isEmpty() && screen.visibleRowIds().stream().allMatch(id ->
                    screen.catalog().state(screen.catalog().binding(id).orElseThrow()) == InputState.OVERLAP),
                    "shared only");
            return null;
        });
        shot("08-filter-shared");
        for (int i = 0; i < 4 && ctx.computeOnClient(mc -> screen.visibleRowIds().size()
                != screen.catalog().bindings().size()); i++) {
            pressButton(popup(screen, 1)); // back to All
        }
        pressButton(category);
        String first = ctx.computeOnClient(mc -> screen.catalog().categories().getFirst());
        check(ctx.computeOnClient(mc -> screen.visibleRowIds().stream().allMatch(id ->
                screen.catalog().binding(id).orElseThrow().category().equals(first))), "category filter");
        int categories = ctx.computeOnClient(mc -> screen.catalog().categories().size());
        for (int i = 0; i < categories; i++) {
            pressButton(popup(screen, 0));
        }
        pressKey(InputConstants.KEY_ESCAPE); // closes the popup
        // Typing while a card is open returns to Explore, with the search applied.
        ctx.runOnClient(mc -> screen.selectInput(InputId.key(SCANCODE_W)));
        click(ctx.computeOnClient(mc -> new int[] {screen.geometry().margin() + 10, screen.geometry().toolbarY() + 10}));
        ctx.getInput().typeChars("key:q");
        ctx.waitTicks(2);
        ctx.computeOnClient(mc -> {
            check(screen.selection().mode() == Selection.Mode.EXPLORE, "typing returns to Explore");
            check(screen.visibleRowIds().size() == 2 && screen.visibleRowIds().contains("key.drop"),
                    "key:q finds both Q actions: " + screen.visibleRowIds());
            return null;
        });
        shot("09-search-highlight-key-q");
        pressKey(InputConstants.KEY_ESCAPE);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == screen && screen.visibleRowIds().size()
                == screen.catalog().bindings().size()), "first Escape clears the search");
    }

    // Per-action remapping in the card with V1 semantics (KMUX-AC-4, KM-AC-R1..R3).
    private void remapping() {
        KeyboardMapScreen screen = current();
        ctx.runOnClient(mc -> screen.selectBinding(DevKeyMappings.ON_Q));
        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CHANGE));
        check(state(screen) == RemapSession.State.CAPTURING, "Change starts capturing");
        shot("10-capturing");
        pressKey(SCANCODE_F7);
        check(state(screen) == RemapSession.State.IDLE, "one input captured");
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.f7"), "remapped to F7");
        check(optionsFileContains("key_" + DevKeyMappings.ON_Q + ":key.keyboard.f7"), "saved by vanilla Options");
        check(ctx.computeOnClient(mc -> InputId.key(SCANCODE_F7).equals(
                screen.selection().highlightedInput(screen.catalog()))), "card follows the action to F7");

        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CHANGE));
        pressKey(InputConstants.KEY_ESCAPE);
        check(state(screen) == RemapSession.State.IDLE, "Escape cancels capture");
        check(ctx.computeOnClient(mc -> mc.gui.screen() == screen
                && screen.selection().mode() == Selection.Mode.INPUT), "Escape during capture keeps the card");
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.f7"), "unchanged after cancel");

        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CHANGE));
        pressKey(SCANCODE_W);
        check(state(screen) == RemapSession.State.CONFIRMING, "overlap with Walk Forward needs confirmation");
        shot("11-confirm-overlap-in-row");
        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CANCEL));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.f7"), "Cancel keeps F7");
        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CHANGE));
        pressKey(SCANCODE_W);
        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CONFIRM));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.w"), "Use anyway keeps the overlap");
        check(ctx.computeOnClient(mc -> screen.cardBindingIds().size() == 2), "card follows to W: two actions");
        shot("12-card-after-overlap");

        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CHANGE));
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_5);
        ctx.waitTicks(2);
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.mouse.5"), "mouse button 5 captured");
        check(ctx.computeOnClient(mc -> InputId.mouse(5).equals(
                screen.selection().highlightedInput(screen.catalog()))), "card follows to mouse 5");
        shot("13-selected-mouse-5");

        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.RESET));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.q"), "Reset restores the declared default");
        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.CLEAR));
        check(mappingKey(DevKeyMappings.ON_Q).equals("key.keyboard.unknown"), "Clear unbinds");
        check(optionsFileContains("key_" + DevKeyMappings.ON_Q + ":key.keyboard.unknown"), "clear saved");
        check(ctx.computeOnClient(mc -> screen.selection().mode() == Selection.Mode.BINDING),
                "cleared action keeps its own card");
        pressButton(row(screen, DevKeyMappings.ON_Q, KeyboardMapTexts.RESET));
        pressKey(InputConstants.KEY_ESCAPE);
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
        pressButton(toolbar(screen, 2));
        on(core -> check(core.choices().get("keyboard_map").settings().getEnum("layout").equals("abnt2"),
                "layout persisted through the Core"));
        KeyboardMapScreen abnt = current();
        ctx.computeOnClient(mc -> {
            check(abnt.inputCenter(InputId.key(100)) != null || abnt.geometry().split(), "key left of Z drawn on ABNT2");
            return null;
        });
        ctx.getInput().resizeWindow(1600, 900);
        ctx.waitTicks(3);
        shot("12-abnt2-1600x900");
        pressButton(toolbar(current(), 2));
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
            KeyboardMapScreen open = current();
            pressButton(ctx.computeOnClient(mc -> open.children().stream().filter(c -> c instanceof Button b
                    && (b.getMessage().equals(Component.translatable(KeyboardMapTexts.KEY_BINDS))
                    || b.getMessage().equals(Component.translatable(KeyboardMapTexts.KEY_BINDS_SHORT))))
                    .map(c -> (Button) c).findFirst().orElseThrow()));
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

    /** The {@code index}-th widget of the toolbar row (0 is the search box). */
    private AbstractWidget toolbar(KeyboardMapScreen screen, int index) {
        return widgetsAt(screen, ctx.computeOnClient(mc -> screen.geometry().toolbarY())).get(index);
    }

    /** The {@code index}-th control of the open Filters popup (0 category, 1 visibility). */
    private AbstractWidget popup(KeyboardMapScreen screen, int index) {
        int y = ctx.computeOnClient(mc -> screen.geometry().toolbarY()) + 26 + index * 24;
        return widgetsAt(screen, y).getFirst();
    }

    private java.util.List<AbstractWidget> widgetsAt(KeyboardMapScreen screen, int y) {
        return ctx.computeOnClient(mc -> screen.children().stream()
                .filter(c -> c instanceof AbstractWidget w && w.getY() == y).map(c -> (AbstractWidget) c)
                .sorted(java.util.Comparator.comparingInt(AbstractWidget::getX)).toList());
    }

    private Button row(KeyboardMapScreen screen, String id, String key) {
        Button button = ctx.computeOnClient(mc -> screen.rowButton(id, key));
        check(button != null, "card button " + key + " for " + id);
        return button;
    }

    private void pressKey(int scancode) {
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(scancode));
        ctx.waitTicks(2);
    }

    private void hover(int[] gui) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(gui[0] * scale + scale / 2, gui[1] * scale + scale / 2);
        ctx.waitTicks(2);
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
