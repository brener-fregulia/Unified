package unified.qol.dev;

import com.mojang.blaze3d.platform.InputConstants;
import unified.qol.QolUnifiedClient;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureState;
import unified.qol.core.SettingsSnapshot;
import unified.qol.feature.shulkerpreview.ShulkerContents;
import unified.qol.feature.shulkerpreview.ShulkerSlotDecorator;
import unified.qol.feature.shulkerpreview.ShulkerSummary;
import unified.qol.feature.shulkerpreview.ShulkerTooltip;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only client game test for Shulker Preview, in a disposable singleplayer world inside
 * {@code build/run-gametest-shulkerpreview}. Boxes are created with commands; tooltips are checked through the game's
 * own tooltip methods and screenshots are saved for visual review.
 */
final class ShulkerPreviewGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");
    private static final String ID = "shulker_preview";
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    private final ClientGameTestContext ctx;
    private TestServerContext server;

    ShulkerPreviewGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    // ---- Boxes ----

    private static String entry(int slot, String item) {
        return "{slot:" + slot + ",item:" + item + "}";
    }

    private static String item(String id, int count) {
        return "{id:\"minecraft:" + id + "\",count:" + count + "}";
    }

    private static String box(String color, List<String> entries) {
        String id = color == null ? "shulker_box" : color + "_shulker_box";
        return id + "[container=[" + String.join(",", entries) + "]]";
    }

    private static List<String> repeat(int slots, String item) {
        return java.util.stream.IntStream.range(0, slots).mapToObj(i -> entry(i, item)).toList();
    }

    private static final String FULL = box("purple", repeat(27, item("stone", 64)));
    private static final String MIXED = box("red", List.of(entry(0, item("cobblestone", 64)),
            entry(1, item("dirt", 64)), entry(2, item("dirt", 64)), entry(5, item("torch", 10)),
            entry(9, item("cobblestone", 32)), entry(13, item("dirt", 64))));
    private static final String SPECIAL = box("blue", List.of(
            entry(0, "{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":5}}}"),
            entry(1, "{id:\"minecraft:iron_pickaxe\",count:1,components:{\"minecraft:damage\":180}}"),
            entry(2, "{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:mending\":1}}}"),
            entry(3, "{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:efficiency\":4}}}"),
            entry(4, "{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:unbreaking\":3}}}"),
            entry(10, "{id:\"minecraft:diamond\",count:1,components:{\"minecraft:custom_name\":\"Lucky gem\",\"minecraft:lore\":[\"From a test\"]}}"),
            entry(26, item("ender_pearl", 16))));
    private static final String TIE = box("white", List.of(entry(1, item("gold_ingot", 32)),
            entry(2, item("iron_ingot", 32))));
    private static final String MANY;
    private static final String HALF = box("black", repeat(14, item("oak_log", 64)));
    private static final String EMPTY = box("lime", List.of());
    private static final String NAMED = "green_shulker_box[custom_name=\"Farm drops\",lore=[\"Keep in base\"],"
            + "container=[" + entry(0, item("wheat", 64)) + "," + entry(1, item("carrot", 20)) + "]]";

    static {
        String[] ids = {"stone", "granite", "diorite", "andesite", "dirt", "sand", "gravel", "clay_ball", "coal",
            "iron_ingot", "gold_ingot", "redstone"};
        List<String> entries = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            entries.add(entry(i, item(ids[i], 64 - i)));
        }
        MANY = box("orange", entries);
    }

    void run() {
        on(core -> check(core.lifecycle().state(ID) == FeatureState.ACTIVE, "enabled by default"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty peaceful");
            server.runCommand("time set noon");
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
            });
            give("hotbar.0", FULL);
            give("hotbar.1", MIXED);
            give("hotbar.2", SPECIAL);
            give("hotbar.3", TIE);
            give("hotbar.4", MANY);
            give("hotbar.5", HALF);
            give("hotbar.6", EMPTY);
            give("hotbar.7", NAMED);
            give("hotbar.8", "shulker_box"); // no component at all: unknown/empty
            for (int i = 0; i < COLORS.length; i++) {
                give("inventory." + i, box(COLORS[i], List.of(entry(i, item("emerald", 1 + i * 4)))));
            }
            give("inventory.16", box(null, List.of(entry(0, item("glowstone_dust", 48)))));
            ctx.waitTicks(5);
            summaries();
            iconSources();
            tooltips();
            inventoryScreens();
            settings();
            sizes();
        }
    }

    // ---- Summaries read from the real component ----

    private void summaries() {
        check(predominant(0) == Items.STONE && summary(0).full() && summary(0).barWidth() == 13, "full stone");
        check(predominant(1) == Items.DIRT && summary(1).predominant().total() == 192, "mixed: dirt 192");
        check(summary(1).usedSlots() == 6 && summary(1).totalItems() == 298, "mixed totals");
        check(predominant(2) == Items.ENDER_PEARL, "special: 16 pearls lead");
        check(summary(2).kinds().stream().anyMatch(k -> k.representative().getItem() == Items.ENCHANTED_BOOK
                && k.total() == 3), "three different books group together");
        check(predominant(3) == Items.GOLD_INGOT, "tie: first slot wins");
        check(summary(4).hiddenKinds() == 2 && summary(4).listedOthers().size() == 9, "12 kinds: 9 listed, +2");
        check(Math.abs(summary(5).fraction() - 14.0 / 27) < 1e-9 && !summary(5).full(), "half box");
        check(summary(6) == null, "known empty contents: nothing");
        check(summary(8) == null, "no contents component: nothing");
        for (int i = 0; i < 17; i++) {
            int slot = 9 + i;
            check(ctx.computeOnClient(mc -> ShulkerContents.summary(mc.player.getInventory().getItem(slot)))
                    != null, "color slot " + slot);
        }
        long builds = ctx.computeOnClient(mc -> ShulkerContents.builds());
        LOGGER.info("Shulker Preview: summaries built {}", builds);
    }

    // ---- Slot icon source (M1.6.1) ----

    private void iconSources() {
        give("inventory.18", box("cyan", List.of(entry(5, item("sand", 12)))));
        give("inventory.19", box("yellow", List.of(entry(0, item("stone", 1)), entry(5, item("sand", 64)),
                entry(6, item("sand", 64)))));
        ctx.waitTicks(3);
        check(source().equals("first_occupied"), "default is first_occupied");
        check(icon(27) == Items.SAND, "slot 0 empty, sand in slot 5: sand");
        check(icon(28) == Items.STONE, "first occupied: stone although sand leads");
        check(icon(1) == Items.COBBLESTONE, "mixed box: cobblestone is in slot 0");
        openInventory();
        hoverHotbar(1);
        shot("29-shift-hint");
        ctx.getInput().setCursorPos(5, 5); // no tooltip over the slots
        ctx.waitTicks(2);
        shot("30-icon-first-occupied");
        set(s -> s.with("slot_icon_source", "predominant"));
        check(icon(28) == Items.SAND && icon(1) == Items.DIRT && icon(3) == Items.GOLD_INGOT,
                "predominant, ties by slot order (LIVE)");
        shot("31-icon-predominant");
        set(s -> s.with("slot_icon", false));
        long frozen = ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated());
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated()) == frozen,
                "slot_icon off wins over either source");
        set(s -> s.with("slot_icon", true).with("slot_icon_source", "first_occupied"));
        check(icon(1) == Items.COBBLESTONE, "back to first occupied without restart");
        ctx.setScreen(() -> null);
    }

    private String source() {
        return ctx.computeOnClient(mc -> unified.qol.feature.shulkerpreview
                .ShulkerPreviewFeature.activeSettings().getEnum("slot_icon_source"));
    }

    /** The slot icon's item for an inventory index (0–8 hotbar, 9–35 main), as the decorator would choose it. */
    private Item icon(int index) {
        return ctx.computeOnClient(mc -> {
            var summary = ShulkerContents.summary(mc.player.getInventory().getItem(index));
            String src = unified.qol.feature.shulkerpreview.ShulkerPreviewFeature
                    .activeSettings().getEnum("slot_icon_source");
            ItemStack stack = summary == null ? null : summary.iconStack(src);
            return stack == null ? null : stack.getItem();
        });
    }

    // ---- Tooltip content (lines and image) ----

    private void tooltips() {
        // Compact by default; the vanilla "Name xN" lines are gone, name, custom name and lore stay.
        check(image(1).filter(t -> t instanceof ShulkerTooltip s && !s.full()).isPresent(), "compact image");
        check(lines(1).stream().noneMatch(l -> l.matches("\\d+ x .+")), "no duplicate vanilla lines: " + lines(1));
        List<String> named = lines(7);
        check(named.contains("Farm drops") && named.contains("Keep in base"), "custom name and lore kept: " + named);
        check(lines(6).size() == 1 && image(6).isEmpty(), "empty box: vanilla tooltip");
        check(image(8).isEmpty(), "no component: vanilla tooltip");

        ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(1);
        check(image(1).filter(t -> t instanceof ShulkerTooltip s && s.full()).isPresent(), "Shift: full image");
        ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(1);
        check(image(1).filter(t -> t instanceof ShulkerTooltip s && !s.full()).isPresent(), "release: compact");

        // Food still gets the Food HUD image.
        check(ctx.computeOnClient(mc -> new ItemStack(Items.BREAD).getTooltipImage()).isPresent(), "Food HUD kept");
    }

    // ---- Screens and screenshots ----

    private void inventoryScreens() {
        long before = ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated());
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
        shot("01-hotbar");
        check(ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated()) > before, "hotbar decorated");

        openInventory();
        shot("02-inventory");
        for (int[] spec : new int[][] {{0, 3}, {1, 4}, {2, 5}, {3, 6}, {4, 7}, {7, 8}}) {
            hoverHotbar(spec[0]);
            shot(String.format("%02d-hover-%d", spec[1], spec[0]));
        }
        hoverHotbar(2);
        ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(2);
        shot("09-shift-special");
        hoverHotbar(0);
        shot("10-shift-full");
        hoverHotbar(4);
        shot("11-shift-many");
        ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(2);
        shot("12-released");

        // A chest with shulkers side by side, like the PO reference.
        ctx.setScreen(() -> null);
        server.runCommand("setblock 0 100 2 chest");
        server.runCommand("fill -1 99 -1 1 99 3 stone");
        for (int i = 0; i < 9; i++) {
            server.runCommand("item replace block 0 100 2 container." + i + " with " + FULL);
        }
        for (int i = 9; i < 14; i++) {
            server.runCommand("item replace block 0 100 2 container." + i + " with " + MIXED);
        }
        server.runCommand("item replace block 0 100 2 container.14 with " + HALF);
        for (int i = 15; i < 26; i++) {
            server.runCommand("item replace block 0 100 2 container." + i
                    + " with purple_shulker_box[container=[" + entry(0, item("stone", 64)) + "]]");
        }
        server.runCommand("tp @a 0.5 100 0.5 0 29");
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitForScreen(ContainerScreen.class);
        ctx.waitTicks(3);
        shot("13-chest");
        hoverSlot(176, 168, 14);
        shot("14-chest-hover-half");
        ctx.setScreen(() -> null);

        // Creative: the hotbar row is shown on every tab.
        server.runCommand("gamemode creative @a");
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> mc.gui.setScreen(new CreativeModeInventoryScreen(mc.player,
                mc.player.connection.enabledFeatures(), true)));
        ctx.waitForScreen(CreativeModeInventoryScreen.class);
        ctx.waitTicks(3);
        shot("15-creative");
        ctx.setScreen(() -> null);
        server.runCommand("gamemode survival @a");
        ctx.waitTicks(3);
    }

    // ---- LIVE settings and the feature toggle ----

    private void settings() {
        openInventory();
        hoverHotbar(1);
        set(s -> s.with("slot_icon", false));
        long frozen = ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated());
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated()) == frozen, "slot_icon off: no icons");
        shot("16-slot-icon-off");
        set(s -> s.with("slot_icon", true).with("compact_preview", false));
        check(image(1).isEmpty() && lines(1).stream().anyMatch(l -> l.matches("\\d+ x .+")),
                "compact off: vanilla lines");
        shot("17-compact-off");
        ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(1);
        check(image(1).filter(t -> t instanceof ShulkerTooltip s && s.full()).isPresent()
                && lines(1).stream().noneMatch(l -> l.matches("\\d+ x .+")), "compact off, Shift: full only");
        ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
        set(s -> s.with("compact_preview", true).with("full_preview_on_shift", false));
        ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(1);
        check(image(1).filter(t -> t instanceof ShulkerTooltip s && !s.full() && !s.shiftHint()).isPresent(),
                "full off: compact without hint, even with Shift");
        ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
        set(s -> s.with("full_preview_on_shift", true));

        setEnabled(false);
        long off = ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated());
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> ShulkerSlotDecorator.decorated()) == off, "disabled: no icons");
        check(image(1).isEmpty() && lines(1).stream().anyMatch(l -> l.matches("\\d+ x .+")),
                "disabled: vanilla tooltip");
        shot("18-disabled");
        setEnabled(true);
        check(image(1).isPresent(), "enabled again");
        ctx.setScreen(() -> null);
    }

    // ---- GUI scales and window sizes ----

    private void sizes() {
        for (int[] window : new int[][] {{1920, 1080}, {854, 480}}) {
            ctx.getInput().resizeWindow(window[0], window[1]);
            for (int scale = 1; scale <= 4; scale++) {
                int s = scale;
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(s);
                    mc.resizeGui();
                });
                ctx.waitTicks(2);
                int applied = (int) (double) ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
                if (applied != s) {
                    continue; // not available at this window size
                }
                openInventory();
                hoverHotbar(4);
                shot("20-" + window[0] + "x" + window[1] + "-gui" + s + "-compact");
                ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
                ctx.waitTicks(2);
                shot("20-" + window[0] + "x" + window[1] + "-gui" + s + "-full");
                ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
                ctx.setScreen(() -> null);
            }
        }
        ctx.getInput().resizeWindow(854, 480);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
    }

    // ---- Helpers ----

    private void give(String slot, String item) {
        server.runCommand("item replace entity @a " + slot + " with " + item);
    }

    private ShulkerSummary<ItemStack> summary(int hotbar) {
        return ctx.computeOnClient(mc -> ShulkerContents.summary(mc.player.getInventory().getItem(hotbar)));
    }

    private Item predominant(int hotbar) {
        return ctx.computeOnClient(mc -> {
            var summary = ShulkerContents.summary(mc.player.getInventory().getItem(hotbar));
            return summary == null ? null : summary.predominant().representative().getItem();
        });
    }

    private Optional<TooltipComponent> image(int hotbar) {
        return ctx.computeOnClient(mc -> mc.player.getInventory().getItem(hotbar).getTooltipImage());
    }

    private List<String> lines(int hotbar) {
        return ctx.computeOnClient(mc -> mc.player.getInventory().getItem(hotbar)
                .getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)
                .stream().map(Component::getString).toList());
    }

    private void openInventory() {
        ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(2);
    }

    /** Survival inventory: 176×166; hotbar slot i at (8 + 18i, 142). */
    private void hoverHotbar(int index) {
        hoverAt(176, 166, 8 + 18 * index, 142);
    }

    /** A slot of the open container screen, by menu index. */
    private void hoverSlot(int imageWidth, int imageHeight, int menuIndex) {
        int[] xy = ctx.computeOnClient(mc -> {
            Slot slot = ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().slots.get(menuIndex);
            return new int[] {slot.x, slot.y};
        });
        hoverAt(imageWidth, imageHeight, xy[0], xy[1]);
    }

    private void hoverAt(int imageWidth, int imageHeight, int slotX, int slotY) {
        double[] pos = ctx.computeOnClient(mc -> {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            double scale = mc.getWindow().getGuiScale();
            return new double[] {((w - imageWidth) / 2 + slotX + 8) * scale,
                ((h - imageHeight) / 2 + slotY + 8) * scale};
        });
        ctx.getInput().setCursorPos(pos[0], pos[1]);
        ctx.waitTicks(2);
    }

    private void set(UnaryOperator<SettingsSnapshot> change) {
        on(core -> {
            var choice = core.choices().get(ID);
            core.saveAndApply(Map.of(ID, choice.withSettings(change.apply(choice.settings()))));
        });
        ctx.waitTicks(2);
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of(ID, core.choices().get(ID).withEnabled(enabled))));
        on(core -> check(core.lifecycle().state(ID) == (enabled ? FeatureState.ACTIVE : FeatureState.INACTIVE),
                "state after toggle"));
        ctx.waitTicks(2);
    }

    private void on(Consumer<CoreRuntime> assertion) {
        ctx.runOnClient(mc -> assertion.accept(QolUnifiedClient.core()));
    }

    private void shot(String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTicks(2);
        ctx.takeScreenshot("shulkerpreview-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
