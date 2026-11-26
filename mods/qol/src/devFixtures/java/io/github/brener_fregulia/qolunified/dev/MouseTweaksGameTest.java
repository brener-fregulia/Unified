package io.github.brener_fregulia.qolunified.dev;

import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.mixin.ContainerScreenSlotsAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only client game test for Inventory Mouse Tweaks (mouse-tweaks-v1.md §10), in a disposable
 * singleplayer world inside {@code build/run-gametest-mousetweaks}. Gestures are driven with real cursor positions
 * and buttons; after each scenario the client's predicted menu is compared slot by slot with the server's, and
 * item totals are checked.
 */
final class MouseTweaksGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");
    private static final String ID = "mouse_tweaks";
    private static final int LEFT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
    private static final String BLOCK = "0 100 2";

    private final ClientGameTestContext ctx;
    private TestServerContext server;
    private final Map<Integer, double[]> centers = new HashMap<>();

    MouseTweaksGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        on(core -> check(core.lifecycle().state(ID) == FeatureState.ACTIVE, "enabled by default"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty peaceful");
            server.runCommand("gamerule randomTickSpeed 0");
            server.runCommand("fill -3 99 -3 3 99 5 stone");
            server.runCommand("fill -3 100 -3 3 103 5 air");
            server.runCommand("tp @a 0.5 100 0.5 0 35");
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
            });
            ctx.waitTicks(5);
            rightDrag();
            leftCollect();
            vanillaEvenSplit();
            shiftDragChest();
            resultSlotsUntouched();
            wheelChest();
            wheelInventoryAndRestrictions();
            furnace();
            cancellation();
            disabled();
            creative();
        }
    }

    // MT-AC-1, MT-AC-2: one item per visit, revisits, restricted and incompatible slots.
    private void rightDrag() {
        clear();
        server.runCommand("item replace entity @a hotbar.0 with stone 10");
        server.runCommand("item replace entity @a inventory.5 with dirt 1"); // menu slot 14
        openInventory();
        int total = total();
        click(36, LEFT); // pick up the 10 stone
        hold(9, RIGHT);
        for (int slot : new int[] {10, 11, 0, 14, 12, 13, 12}) { // 0: crafting result, 14: dirt, 12 revisited
            moveTo(slot);
        }
        release(RIGHT);
        check(count(9) == 1 && count(10) == 1 && count(11) == 1 && count(13) == 1, "one per visited slot: " + ctx.computeOnClient(mc -> describe(menu(mc))));
        check(count(12) == 2, "a revisit places another item, was " + count(12));
        check(count(0) == 0, "nothing in the crafting result");
        check(item(14).getItem().toString().contains("dirt") && count(14) == 1, "incompatible slot untouched");
        check(carried() == 4, "the remainder stays on the cursor, was " + carried());
        shot("01-right-drag");
        click(36, LEFT);
        synced(total);
    }

    // MT-AC-3: collection into the cursor, remainders, maximum stack.
    private void leftCollect() {
        clear();
        server.runCommand("item replace entity @a inventory.11 with stone 5"); // menu slot 20
        server.runCommand("item replace entity @a inventory.12 with stone 5"); // 21
        server.runCommand("item replace entity @a inventory.13 with stone 60"); // 22: would overflow, skipped
        server.runCommand("item replace entity @a inventory.14 with stone 3"); // 23
        openInventory();
        int total = total();
        click(23, LEFT); // cursor: 3 stone
        hold(20, LEFT);
        moveTo(21);
        moveTo(22);
        release(LEFT);
        check(carried() == 13, "3 + 5 + 5 collected, was " + carried());
        check(count(20) == 0 && count(21) == 0, "collected slots emptied");
        check(count(22) == 60, "a slot that would overflow the cursor is skipped");
        click(23, LEFT);
        synced(total);
    }

    // Q-MT3: a first slot that does not match keeps vanilla's even split.
    private void vanillaEvenSplit() {
        clear();
        server.runCommand("item replace entity @a hotbar.0 with stone 9");
        openInventory();
        int total = total();
        click(36, LEFT);
        hold(27, LEFT);
        moveTo(28);
        moveTo(29);
        release(LEFT);
        check(count(27) == 3 && count(28) == 3 && count(29) == 3, "vanilla even split, was " + count(27) + "/"
                + count(28) + "/" + count(29));
        synced(total);
    }

    // MT-AC-4: Shift + drag quick-moves each stack once, with the chest's rules.
    private void shiftDragChest() {
        clear();
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:10},{Slot:1b,id:\"dirt\",count:5},"
                + "{Slot:2b,id:\"oak_log\",count:3}]}");
        int total = total();
        shiftHold(0);
        moveTo(1);
        moveTo(2);
        moveTo(1); // revisit: nothing more
        shiftRelease();
        check(count(0) == 0 && count(1) == 0 && count(2) == 0, "chest slots quick-moved: " + ctx.computeOnClient(mc -> describe(menu(mc))));
        shot("02-shift-drag-chest");
        synced(total);
        close();
    }

    // MT-AC-5: dragging over a crafting result never crafts.
    private void resultSlotsUntouched() {
        clear();
        server.runCommand("item replace entity @a hotbar.0 with oak_log 4");
        openBlock("crafting_table", "");
        // Crafting menu: 0 result, 1–9 grid, 10–36 main, 37–45 hotbar.
        click(37, LEFT);
        click(1, RIGHT); // one log in the grid: the result shows 4 planks
        click(37, LEFT);
        check(count(0) == 4, "result preview present");
        shiftHold(10);
        moveTo(0);
        moveTo(1);
        shiftRelease();
        check(count(1) == 0, "the grid input was quick-moved like any stack");
        check(countOf("planks") == 0 && countOf("oak_log") == 4, "nothing crafted from the result slot");
        synced(4); // the four logs; the result preview vanished with its input
        close();
    }

    // MT-AC-6, MT-AC-7, MT-AC-9: push, pull, fractional deltas, the per-event cap, nonstackables, search order.
    private void wheelChest() {
        clear();
        openBlock("chest", "{Items:[{Slot:0b,id:\"stone\",count:20},{Slot:1b,id:\"diamond_sword\",count:1}]}");
        int total = total();
        // Chest menu: 0–26 chest, 27–53 main, 54–62 hotbar. last_to_first: the last hotbar slot is first.
        scrollOver(0, -1);
        check(count(0) == 19 && count(62) == 1, "one notch down pushes one, to the last free slot");
        scrollOver(0, -1);
        check(count(62) == 2, "a matching stack with room comes first");
        scrollOver(0, 1);
        check(count(0) == 19 && count(62) == 1, "one notch up pulls one back, was " + count(0) + "/" + count(62));
        // MT-AC-17: two large wheel events in the same client tick. The first uses the whole 24-click budget
        // (8 three-click transfers); the second finds no budget and moves nothing, and nothing is queued.
        moveTo(0);
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            mouse.invokeOnScroll(mc.getWindow().handle(), 0, -20);
            mouse.invokeOnScroll(mc.getWindow().handle(), 0, -20);
        });
        ctx.waitTicks(3);
        check(count(0) == 11 && count(62) == 9, "8 items, then none in the same tick, was " + count(0));
        synced(total);
        close();
    }

    private void wheelInventoryAndRestrictions() {
        clear();
        server.runCommand("item replace entity @a inventory.0 with stone 20"); // menu slot 9
        server.runCommand("item replace entity @a inventory.1 with diamond_sword 1"); // 10
        server.runCommand("item replace entity @a inventory.2 with bundle[bundle_contents=[{id:\"stone\",count:1}]] 1"); // 11
        openInventory();
        int total = total();
        for (int i = 0; i < 4; i++) {
            scrollOver(9, -0.25);
        }
        check(count(9) == 19 && count(44) == 1, "four quarter notches move one item to the hotbar, last first");
        scrollOver(9, -20);
        check(count(9) == 11, "one event moves at most 8, was " + count(9));
        scrollOver(44, -1);
        check(count(44) == 8 && count(9) == 12, "the hotbar pushes back onto the main stack");
        scrollOver(10, -1);
        check(count(10) == 0 && count(43) == 1, "a nonstackable moves whole, to the next free hotbar slot");
        int bundleBefore = count(11);
        scrollOver(11, -1);
        check(count(11) == bundleBefore, "the bundle keeps vanilla's wheel");
        setSetting("wheel_direction", "inverted");
        int before = count(9);
        scrollOver(9, 1);
        check(count(9) == before - 1, "inverted: up pushes");
        setSetting("wheel_direction", "normal");
        setSetting("wheel_search_order", "first_to_last");
        server.runCommand("item replace entity @a inventory.20 with oak_log 3"); // 29
        ctx.waitTicks(2);
        scrollOver(29, -1);
        check(count(36) == 1, "first_to_last: the first hotbar slot, was " + count(36));
        setSetting("wheel_search_order", "last_to_first");
        shot("03-wheel-inventory");
        synced(total + 3); // the three logs added above
        close();
    }

    // Restricted slots: furnace input and fuel rules, result excluded.
    private void furnace() {
        clear();
        // Dirt neither smelts nor burns, so the furnace never starts and the totals stay exact.
        server.runCommand("item replace entity @a hotbar.0 with dirt 4");
        server.runCommand("item replace entity @a hotbar.1 with coal 4");
        openBlock("furnace", "");
        // Furnace menu: 0 input, 1 fuel, 2 result, 3–29 main, 30–38 hotbar.
        int total = total();
        scrollOver(30, -1);
        check(count(0) == 1 && count(1) == 0, "dirt goes to the input slot, never the fuel or result");
        scrollOver(31, -1);
        check(count(1) == 1 && count(0) == 1, "coal goes to the fuel slot");
        check(count(2) == 0, "result untouched");
        synced(total);
        close();
    }

    // Gesture cancellation: closing the screen mid-drag sends nothing more.
    private void cancellation() {
        clear();
        server.runCommand("item replace entity @a hotbar.0 with stone 10");
        openInventory();
        click(36, LEFT);
        hold(9, RIGHT);
        moveTo(10);
        close(); // the normal close path: the carried stack goes back into the inventory
        openInventory();
        ctx.getInput().releaseMouse(RIGHT); // released over the new screen, never in the world
        ctx.waitTicks(2);
        check(total() == 10 && carried() == 0, "nothing lost or dropped: " + ctx.computeOnClient(mc ->
                describe(menu(mc))));
        String before = ctx.computeOnClient(mc -> describe(menu(mc)));
        moveTo(11);
        moveTo(12);
        check(before.equals(ctx.computeOnClient(mc -> describe(menu(mc)))), "no clicks after the cancelled gesture");
        synced(10);
        close();
    }

    // MT-AC-13: OFF restores vanilla's right-drag (one per slot, no revisits).
    private void disabled() {
        setEnabled(false);
        clear();
        server.runCommand("item replace entity @a hotbar.0 with stone 10");
        openInventory();
        click(36, LEFT);
        hold(9, RIGHT);
        moveTo(10);
        moveTo(9);
        release(RIGHT);
        check(count(9) == 1 && count(10) == 1, "vanilla right-drag: no second item on revisit");
        click(36, LEFT);
        synced(10);
        close();
        setEnabled(true);
    }

    // MT-AC-12: the creative inventory is untouched.
    private void creative() {
        clear();
        server.runCommand("gamemode creative @a");
        server.runCommand("item replace entity @a hotbar.0 with stone 10");
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitForScreen(CreativeModeInventoryScreen.class);
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> check(!(mc.gui.screen() instanceof InventoryScreen), "creative screen"));
        locateSlots();
        int stone = ctx.computeOnClient(mc -> menu(mc).slots.stream().filter(sl -> sl.getItem().getCount() == 10)
                .mapToInt(sl -> sl.index).findFirst().orElse(-1));
        check(stone >= 0 && centers.containsKey(stone), "hotbar stack visible in the creative inventory");
        scrollOver(stone, -3); // would push three items with the wheel outside creative
        check(ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).getCount()) == 10,
                "the wheel does nothing in the creative inventory");
        shot("04-creative");
        close();
        check(ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).getCount()) == 10, "unchanged");
        server.runCommand("gamemode survival @a");
    }

    // ---- Helpers ----

    private void clear() {
        close();
        server.runCommand("clear @a");
        server.runCommand("setblock " + BLOCK + " air");
        ctx.waitTicks(2);
    }

    private void openInventory() {
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(2);
        locateSlots();
    }

    private void openBlock(String block, String nbt) {
        server.runCommand("setblock " + BLOCK + " " + block + nbt);
        server.runCommand("tp @a 0.5 100 0.5 0 35");
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>);
        ctx.waitTicks(2);
        locateSlots();
    }

    private void close() {
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() != null) {
                mc.gui.screen().onClose();
            }
        });
        ctx.waitTicks(2);
    }

    /** Finds each slot's center in GUI coordinates through the same lookup the gestures use. */
    private void locateSlots() {
        centers.clear();
        ctx.runOnClient(mc -> {
            var screen = (AbstractContainerScreen<?>) mc.gui.screen();
            var accessor = (ContainerScreenSlotsAccessor) screen;
            Map<Integer, int[]> bounds = new HashMap<>();
            for (int x = 0; x < screen.width; x++) {
                for (int y = 0; y < screen.height; y++) {
                    Slot slot = accessor.qolUnified$slotAt(x + 0.5, y + 0.5);
                    if (slot != null) {
                        int px = x;
                        int py = y;
                        int[] b = bounds.computeIfAbsent(slot.index, i -> new int[] {px, py, px, py});
                        b[0] = Math.min(b[0], x);
                        b[1] = Math.min(b[1], y);
                        b[2] = Math.max(b[2], x);
                        b[3] = Math.max(b[3], y);
                    }
                }
            }
            bounds.forEach((i, b) -> centers.put(i, new double[] {(b[0] + b[2]) / 2.0 + 0.5,
                    (b[1] + b[3]) / 2.0 + 0.5}));
        });
    }

    private void moveTo(int slot) {
        double[] c = centers.get(slot);
        check(c != null, "slot " + slot + " on screen");
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(c[0] * scale, c[1] * scale);
        ctx.waitTicks(1);
        if (debugMoves-- > 0) {
            LOGGER.info("[mousetweaks] move to {} at {},{} scale {} → mouse {},{} window {}x{} screen {}x{}", slot,
                    c[0], c[1], scale, ctx.computeOnClient(mc -> mc.mouseHandler.xpos()),
                    ctx.computeOnClient(mc -> mc.mouseHandler.ypos()),
                    ctx.computeOnClient(mc -> mc.getWindow().getWidth()),
                    ctx.computeOnClient(mc -> mc.getWindow().getHeight()),
                    ctx.computeOnClient(mc -> mc.gui.screen().width),
                    ctx.computeOnClient(mc -> mc.gui.screen().height));
        }
    }

    private int debugMoves = 3;

    private void hold(int slot, int button) {
        moveTo(slot);
        ctx.getInput().holdMouse(button);
        ctx.waitTicks(1);
        // A real hand moves a little inside the pressed slot; vanilla's drag counts that slot only then.
        double[] c = centers.get(slot);
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos((c[0] + 1) * scale, c[1] * scale);
        ctx.waitTicks(1);
    }

    /**
     * Fabric's test input always sends mouse presses without modifiers, so a Shift-click is sent through the same
     * MouseHandler.onButton path it uses, with the Shift modifier set.
     */
    private void shiftHold(int slot) {
        moveTo(slot);
        shiftButton(com.mojang.blaze3d.platform.InputConstants.PRESS);
        double[] c = centers.get(slot);
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos((c[0] + 1) * scale, c[1] * scale);
        ctx.waitTicks(1);
    }

    private void shiftRelease() {
        shiftButton(com.mojang.blaze3d.platform.InputConstants.RELEASE);
        ctx.waitTicks(2);
    }

    private void shiftButton(int action) {
        ctx.runOnClient(mc -> ((net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler)
                .invokeOnButton(mc.getWindow().handle(), new net.minecraft.client.input.MouseButtonInfo(LEFT,
                        com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT), action));
        ctx.waitTicks(1);
    }

    private void release(int button) {
        ctx.getInput().releaseMouse(button);
        ctx.waitTicks(2);
    }

    private void click(int slot, int button) {
        hold(slot, button);
        release(button);
    }

    private void scrollOver(int slot, double amount) {
        moveTo(slot);
        ctx.getInput().scroll(amount);
        ctx.waitTicks(2);
    }

    private AbstractContainerMenu menu(Minecraft mc) {
        return mc.player.containerMenu;
    }

    private int count(int slot) {
        return ctx.computeOnClient(mc -> menu(mc).getSlot(slot).getItem().getCount());
    }

    private ItemStack item(int slot) {
        return ctx.computeOnClient(mc -> menu(mc).getSlot(slot).getItem().copy());
    }

    private int carried() {
        return ctx.computeOnClient(mc -> menu(mc).getCarried().getCount());
    }

    private int countOf(String id) {
        return ctx.computeOnClient(mc -> menu(mc).slots.stream().filter(s -> s.getItem().getItem().toString()
                .contains(id)).mapToInt(s -> s.getItem().getCount()).sum());
    }

    /** Items in the open menu plus the cursor. */
    private int total() {
        return ctx.computeOnClient(mc -> menu(mc).slots.stream().mapToInt(s -> s.getItem().getCount()).sum()
                + menu(mc).getCarried().getCount());
    }

    /** MT-AC-10, MT-AC-11: totals unchanged, and the server's menu matches the client's slot by slot. */
    private void synced(int expectedTotal) {
        ctx.waitTicks(3);
        check(total() == expectedTotal, "item total " + expectedTotal + " → " + total());
        String client = ctx.computeOnClient(mc -> describe(menu(mc)));
        String serverSide = server.computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            return describe(p.containerMenu);
        });
        check(client.equals(serverSide), "client and server agree:\nclient " + client + "\nserver " + serverSide);
        LOGGER.info("[mousetweaks] synced: {}", client);
    }

    private static String describe(AbstractContainerMenu menu) {
        StringBuilder out = new StringBuilder();
        for (Slot slot : menu.slots) {
            if (!slot.getItem().isEmpty()) {
                out.append(slot.index).append('=').append(slot.getItem().getCount()).append(' ')
                        .append(slot.getItem().getItem()).append("; ");
            }
        }
        return out.append("cursor=").append(menu.getCarried().getCount()).toString();
    }

    private void setSetting(String key, Object value) {
        on(core -> {
            var choice = core.choices().get(ID);
            core.saveAndApply(Map.of(ID, choice.withSettings(choice.settings().with(key, value))));
        });
        ctx.waitTicks(1);
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of(ID, core.choices().get(ID).withEnabled(enabled))));
        ctx.waitTicks(1);
    }

    private void on(Consumer<CoreRuntime> action) {
        ctx.runOnClient(mc -> action.accept(QolUnifiedClient.core()));
    }

    private void shot(String name) {
        ctx.takeScreenshot("mousetweaks-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
