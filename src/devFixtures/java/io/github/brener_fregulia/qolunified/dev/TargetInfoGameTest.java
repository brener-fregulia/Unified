package io.github.brener_fregulia.qolunified.dev;

import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.feature.targetinfo.TargetInfoRenderer;
import io.github.brener_fregulia.qolunified.feature.targetinfo.TargetView;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Mark;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only client game test for Target Info, in a disposable singleplayer world inside
 * {@code build/run-gametest-targetinfo}. Blocks are placed in front of the player with commands and read back
 * through the overlay; screenshots are saved for visual review.
 */
final class TargetInfoGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");
    private static final String ID = "target_info";
    private static final BlockPos TARGET = new BlockPos(0, 100, 2);

    private final ClientGameTestContext ctx;
    private TestServerContext server;

    TargetInfoGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        on(core -> check(core.lifecycle().state(ID) == FeatureState.ACTIVE, "enabled by default"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty peaceful");
            server.runCommand("time set noon");
                        server.runCommand("gamerule randomTickSpeed 0");
            server.runCommand("fill -3 99 -3 3 99 5 stone");
            server.runCommand("fill -3 100 -3 3 103 5 air");
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
            });
            lookAtTarget();
            blocks();
            mining();
            states();
            entities();
            visibility();
            guiScales();
            settings();
        }
    }

    /**
     * TI-AC-16, with Jade 26.3.5+fabric loaded: Target Info starts disabled by the conflict and the same scenes are
     * captured with Jade drawing alone, for side-by-side review. An explicit enable then wins over the default.
     */
    void compareWithJade() {
        check(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jade"), "Jade loaded");
        on(core -> {
            check(core.lifecycle().state(ID) == FeatureState.INACTIVE, "conflict default disables");
            check(core.compatibility(ID).defaultSuppressed(), "suppressed by Jade");
        });
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty peaceful");
            server.runCommand("time set noon");
            server.runCommand("gamerule randomTickSpeed 0");
            server.runCommand("fill -3 99 -3 3 99 5 stone");
            server.runCommand("fill -3 100 -3 3 103 5 air");
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
            });
            lookAtTarget();
            for (boolean ours : new boolean[] {false, true}) {
                if (ours) {
                    setEnabled(true); // explicit choice wins; both overlays now draw
                    on(core -> check(core.choices().get(ID).explicitEnabled(), "explicit choice recorded"));
                }
                String tag = ours ? "both-" : "jade-";
                hold(ItemStack.EMPTY);
                place("stone");
                shot(tag + "01-stone-hand");
                hold(new ItemStack(Items.IRON_PICKAXE));
                place("obsidian");
                shot(tag + "06-obsidian-iron");
                hold(ItemStack.EMPTY);
                place("oak_leaves[persistent=true]");
                shot(tag + "08-leaves");
                place("obsidian");
                ctx.getInput().holdKey(o -> o.keyAttack);
                ctx.waitTicks(12);
                shot(tag + "11-mining-obsidian");
                ctx.getInput().releaseKey(o -> o.keyAttack);
                place("beehive[honey_level=3]");
                shot(tag + "12-beehive-3");
                server.runCommand("tp @a 0.5 100 0.5 0 39");
                server.runCommand("setblock 1 100 2 redstone_block");
                place("redstone_wire");
                shot(tag + "13-wire-15");
                server.runCommand("setblock 1 100 2 air");
                lookAtTarget();
                place("air");
                server.runCommand("summon sheep 0.5 100 2.5 {NoAI:1b,Silent:1b,Health:5f}");
                ctx.waitTicks(5);
                shot(tag + "18-sheep");
                server.runCommand("tp @e[type=sheep] 0 -200 0");
        server.runCommand("kill @e[type=sheep]");
                server.runCommand("summon wolf 0.5 100 2.5 {NoAI:1b,Silent:1b}");
                ctx.waitTicks(5);
                server.runOnServer(srv -> {
                    ServerPlayer player = srv.getPlayerList().getPlayers().getFirst();
                    for (Wolf wolf : srv.overworld().getEntitiesOfClass(Wolf.class,
                            player.getBoundingBox().inflate(8))) {
                        wolf.tame(player);
                    }
                });
                ctx.waitTicks(5);
                shot(tag + "20-wolf-owner");
                server.runCommand("tp @e[type=wolf] 0 -200 0");
        server.runCommand("kill @e[type=wolf]");
                ctx.waitTicks(3);
            }
        }
    }

    // ---- Blocks and tools (TI-AC-1 to TI-AC-5) ----

    private void blocks() {
        hold(ItemStack.EMPTY);
        place("stone");
        expect("Stone", "Minecraft", List.of(Items.WOODEN_PICKAXE), Mark.CROSS);
        expectLine("Requires: Wooden Pickaxe");
        shot("01-stone-hand");
        hold(new ItemStack(Items.WOODEN_PICKAXE));
        expect("Stone", "Minecraft", List.of(Items.WOODEN_PICKAXE), Mark.CHECK);
        shot("02-stone-wooden-pickaxe");

        hold(ItemStack.EMPTY);
        place("grass_block");
        expect("Grass Block", "Minecraft", List.of(Items.WOODEN_SHOVEL), Mark.NONE);
        shot("03-grass-hand");
        hold(new ItemStack(Items.IRON_SHOVEL));
        expect("Grass Block", "Minecraft", List.of(Items.WOODEN_SHOVEL), Mark.CHECK);

        hold(ItemStack.EMPTY);
        place("oak_log");
        expect("Oak Log", "Minecraft", List.of(Items.WOODEN_AXE), Mark.NONE);
        shot("04-oak-log");

        place("iron_ore");
        hold(new ItemStack(Items.WOODEN_PICKAXE));
        expect("Iron Ore", "Minecraft", List.of(Items.STONE_PICKAXE), Mark.CROSS);
        expectLine("Requires: Stone Pickaxe");
        shot("05-iron-ore-wooden");
        hold(new ItemStack(Items.STONE_PICKAXE));
        expect("Iron Ore", "Minecraft", List.of(Items.STONE_PICKAXE), Mark.CHECK);

        place("diamond_ore");
        hold(new ItemStack(Items.STONE_PICKAXE));
        expect("Diamond Ore", "Minecraft", List.of(Items.IRON_PICKAXE), Mark.CROSS);

        place("obsidian");
        hold(new ItemStack(Items.IRON_PICKAXE));
        expect("Obsidian", "Minecraft", List.of(Items.DIAMOND_PICKAXE), Mark.CROSS);
        expectLine("Requires: Diamond Pickaxe");
        shot("06-obsidian-iron");
        hold(new ItemStack(Items.DIAMOND_PICKAXE));
        expect("Obsidian", "Minecraft", List.of(Items.DIAMOND_PICKAXE), Mark.CHECK);
        shot("07-obsidian-diamond");
        hold(new ItemStack(Items.NETHERITE_PICKAXE));
        expect("Obsidian", "Minecraft", List.of(Items.DIAMOND_PICKAXE), Mark.CHECK);

        hold(ItemStack.EMPTY);
        place("oak_leaves[persistent=true]");
        TargetView leaves = drawn();
        log("oak leaves", leaves);
        check(toolItems(leaves).contains(Items.WOODEN_HOE) && toolItems(leaves).contains(Items.SHEARS),
                "leaves: hoe and shears side by side");
        check(leaves.mark() == Mark.NONE, "leaves: the hand is not wrong");
        shot("08-leaves");

        place("cobweb");
        TargetView web = drawn();
        log("cobweb", web);
        check(toolItems(web).contains(Items.SHEARS), "cobweb: shears");
        check(toolItems(web).stream().anyMatch(i -> i == Items.WOODEN_SWORD), "cobweb: sword");
        shot("09-cobweb");

        place("torch");
        expect("Torch", "Minecraft", List.of(), Mark.NONE);
        shot("10-torch");
        place("poppy");
        expect("Poppy", "Minecraft", List.of(), Mark.NONE);
    }

    // ---- Mining progress (TI-AC-6) ----

    private void mining() {
        place("obsidian");
        hold(ItemStack.EMPTY); // about 250 s by hand: never finishes during the test
        ctx.getInput().holdKey(o -> o.keyAttack);
        List<Float> seen = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ctx.waitTick();
            seen.add(ctx.computeOnClient(mc -> TargetInfoRenderer.lastProgress()));
        }
        shot("11-mining-obsidian");
        LOGGER.info("Target Info progress samples: {}", seen);
        check(seen.getLast() > 0.0f && seen.getLast() < 1.0f, "a bar is drawn while breaking");
        check(seen.stream().distinct().count() >= 8, "the bar moves every tick (smooth, not 10 steps)");
        // Target change: the bar disappears at once.
        server.runCommand("setblock 0 100 2 air");
        server.runCommand("setblock 1 100 2 obsidian");
        ctx.waitTicks(2);
        ctx.getInput().releaseKey(o -> o.keyAttack);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> TargetInfoRenderer.lastProgress()) < 0, "no bar after stopping");
        server.runCommand("setblock 1 100 2 air");

        // Breaking stone with a pickaxe completes; the time is the game's own.
        place("stone");
        hold(new ItemStack(Items.WOODEN_PICKAXE));
        ctx.getInput().holdKey(o -> o.keyAttack);
        float max = 0.0f;
        for (int i = 0; i < 40 && !ctx.computeOnClient(mc -> mc.level.getBlockState(TARGET).isAir()); i++) {
            ctx.waitTick();
            max = Math.max(max, ctx.computeOnClient(mc -> TargetInfoRenderer.lastProgress()));
        }
        ctx.getInput().releaseKey(o -> o.keyAttack);
        check(ctx.computeOnClient(mc -> mc.level.getBlockState(TARGET).isAir()), "stone breaks normally");
        check(max > 0.5f, "the bar filled while breaking stone: " + max);
        ctx.waitTicks(2);
        // The crosshair now reaches the floor behind: the overlay follows the new target, with no stale bar.
        TargetView after = drawn();
        check(!TARGET.equals(after.pos()) && TargetInfoRenderer.lastProgress() < 0, "follows the new target");

        // A visible bar for the screenshot: dirt by hand takes about 15 ticks.
        place("dirt");
        hold(ItemStack.EMPTY);
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.getInput().holdKey(o -> o.keyAttack);
        ctx.waitTicks(12); // after a 5-tick cooldown from the previous break
        float dirt = ctx.computeOnClient(mc -> TargetInfoRenderer.lastProgress());
        ctx.takeScreenshot("targetinfo-11b-mining-dirt");
        ctx.getInput().releaseKey(o -> o.keyAttack);
        check(dirt > 0.2f && dirt < 1.0f, "dirt bar part-way: " + dirt);

        // Creative: no bar.
        server.runCommand("gamemode creative @a");
        place("obsidian");
        ctx.getInput().holdKey(o -> o.keyAttack);
        ctx.waitTicks(1);
        float creative = ctx.computeOnClient(mc -> TargetInfoRenderer.lastProgress());
        ctx.getInput().releaseKey(o -> o.keyAttack);
        check(creative < 0, "no bar in Creative");
        server.runCommand("gamemode survival @a");
    }

    // ---- Block states (TI-AC-7 to TI-AC-9) ----

    private void states() {
        hold(ItemStack.EMPTY);
        for (int honey = 0; honey <= 5; honey++) {
            place("beehive[honey_level=" + honey + "]");
            expectLine("Honey: " + honey + "/5");
        }
        check(drawn().lines().stream().noneMatch(l -> l.getString().contains("Bee")), "no bee count");
        shot("12-beehive-5");
        place("bee_nest[honey_level=2]");
        expectLine("Honey: 2/5");

        // Thin blocks: aim at the bottom centre of the block (pitch 39) so the ray meets them.
        server.runCommand("tp @a 0.5 100 0.5 0 39");
        server.runCommand("setblock 1 100 2 redstone_block");
        place("redstone_wire");
        expectLine("Power: " + stateValue("power"));
        expectLine("Power: 15");
        shot("13-wire-15");
        server.runCommand("setblock 1 100 2 air");
        ctx.waitTicks(3);
        expectLine("Power: 0");

        place("lever[face=floor,powered=true]");
        expectLine("Lever: On");
        shot("14-lever-on");
        place("lever[face=floor,powered=false]");
        expectLine("Lever: Off");
        place("stone_button[face=floor]");
        expectLine("Button: Released");
        place("repeater[delay=3]");
        expectLine("Delay: 3 ticks");
        expectLine("Unpowered");
        shot("15-repeater");
        place("comparator[mode=subtract]");
        expectLine("Mode: Subtract");
        place("comparator[mode=compare]");
        expectLine("Mode: Compare");
        lookAtTarget();
        place("redstone_lamp");
        expectLine("Lamp: Off");
        server.runCommand("setblock 1 100 2 redstone_block"); // the neighbor update lights it
        ctx.waitTicks(3);
        expectLine("Lamp: On");
        shot("16-lamp-on");
        server.runCommand("setblock 1 100 2 air");

        server.runCommand("tp @a 0.5 100 0.5 0 39");
        server.runCommand("setblock 0 99 2 farmland");
        place("wheat[age=0]");
        expectLine("Growth: 0%");
        place("wheat[age=4]");
        expectLine("Growth: 57%");
        shot("17-wheat-57");
        place("wheat[age=7]");
        expectLine("Mature");
        server.runCommand("setblock 0 99 2 soul_sand");
        place("nether_wart[age=3]");
        expectLine("Mature");
        server.runCommand("setblock 0 99 2 stone");
        place("air");
        lookAtTarget();
    }

    // ---- Entities (TI-AC-10) ----

    private void entities() {
        server.runCommand("summon sheep 0.5 100 2.5 {NoAI:1b,Silent:1b,Health:5f}");
        ctx.waitTicks(5);
        TargetView sheep = drawn();
        log("sheep", sheep);
        check(sheep.name().getString().equals("Sheep"), "sheep name");
        check(sheep.health() != null && sheep.health().hearts() && sheep.health().halves() == 5
                && sheep.health().maxHalves() == 8, "sheep hearts 5/8");
        check(sheep.source() != null && sheep.source().getString().equals("Minecraft"), "sheep source");
        shot("18-sheep");
        server.runCommand("tp @e[type=sheep] 0 -200 0");
        server.runCommand("kill @e[type=sheep]");

        server.runCommand("summon wolf 0.5 100 2.5 {NoAI:1b,Silent:1b}");
        ctx.waitTicks(5);
        check(drawn().name().getString().equals("Wolf") && drawn().lines().isEmpty(), "untamed wolf: no owner line");
        shot("19-wolf-untamed");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
            for (Wolf wolf : s.overworld().getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(8))) {
                wolf.tame(player);
            }
        });
        ctx.waitTicks(5);
        String player = ctx.computeOnClient(mc -> mc.player.getName().getString());
        expectLine("Owner: " + player);
        shot("20-wolf-owner");
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            for (Wolf wolf : s.overworld().getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(8))) {
                wolf.setOwnerReference(EntityReference.of(UUID.fromString("2b6c4c1e-0000-4000-8000-00000000abcd")));
            }
        });
        ctx.waitTicks(5);
        expectLine("Owner: unknown player");
        check(drawn().lines().stream().noneMatch(l -> l.getString().contains("2b6c4c1e")), "UUID never shown");
        shot("21-wolf-unknown-owner");
        server.runCommand("tp @e[type=wolf] 0 -200 0");
        server.runCommand("kill @e[type=wolf]");

        server.runCommand("summon iron_golem 0.5 100 2.5 {NoAI:1b,Silent:1b,Health:83f}");
        ctx.waitTicks(5);
        TargetView golem = drawn();
        check(golem.health() != null && !golem.health().hearts(), "golem: numeric health above 10 hearts");
        expectHealth("Health: 83 / 100");
        shot("22-iron-golem");
        server.runCommand("tp @e[type=iron_golem] 0 -200 0");
        server.runCommand("kill @e[type=iron_golem]");
        ctx.waitTicks(3);
    }

    // ---- Suppression and boss bars (TI-AC-11, TI-AC-12) ----

    private void visibility() {
        place("stone");
        check(drawn() != null, "stone shown");
        server.runCommand("tp @a 0.5 100 0.5 0 -80");
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> TargetInfoRenderer.lastDrawn()) == null, "sky: nothing");
        lookAtTarget();
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        ctx.waitTicks(8); // past lastDrawn()'s quarter-second staleness window
        check(ctx.computeOnClient(mc -> TargetInfoRenderer.lastDrawn()) == null, "F1: nothing");
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        ctx.setScreen(() -> new InventoryScreen(ctx.computeOnClient(mc -> mc.player)));
        ctx.waitTicks(8); // past lastDrawn()'s quarter-second staleness window
        check(ctx.computeOnClient(mc -> TargetInfoRenderer.lastDrawn()) == null, "screen open: nothing");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
        check(drawn() != null, "shown again");

        int before = bounds()[1];
        server.runCommand("bossbar add qol_unified_dev:a \"Test boss A\"");
        server.runCommand("bossbar set qol_unified_dev:a players @a");
        server.runCommand("bossbar add qol_unified_dev:b \"Test boss B\"");
        server.runCommand("bossbar set qol_unified_dev:b players @a");
        ctx.waitTicks(3);
        int after = bounds()[1];
        check(after >= 12 + 19 + 5, "below two boss bars: y " + after + " (was " + before + ")");
        shot("23-boss-bars");
        server.runCommand("bossbar remove qol_unified_dev:a");
        server.runCommand("bossbar remove qol_unified_dev:b");
        ctx.waitTicks(3);
        check(bounds()[1] == before, "back to the top");
    }

    private void guiScales() {
        place("obsidian");
        hold(new ItemStack(Items.IRON_PICKAXE));
        // Large enough that every GUI scale 1–4 is available.
        ctx.getInput().resizeWindow(1920, 1080);
        for (int scale = 1; scale <= 4; scale++) {
            int s = scale;
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(s);
                mc.resizeGui();
            });
            ctx.waitTicks(3);
            int[] b = bounds();
            int width = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
            check(Math.abs(2 * b[0] + b[2] - width) <= 1, "centered at scale " + s);
            check(b[0] >= 0 && b[0] + b[2] <= width && b[1] >= 0, "inside the screen at scale " + s);
            check(ctx.computeOnClient(mc -> mc.getWindow().getGuiScale()) == s, "scale " + s + " applied");
            shot("24-gui-scale-" + s);
        }
        ctx.getInput().resizeWindow(854, 480);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
    }

    // ---- LIVE settings (TI-AC-14) ----

    private void settings() {
        place("obsidian");
        hold(new ItemStack(Items.IRON_PICKAXE));
        set(s -> s.with("show_tools", false));
        check(drawn().tools().isEmpty() && drawn().mark() == Mark.NONE, "show_tools off");
        set(s -> s.with("show_tools", true).with("show_source", false));
        check(drawn().source() == null && !drawn().tools().isEmpty(), "show_source off");
        set(s -> s.with("show_source", true).with("show_blocks", false));
        check(ctx.computeOnClient(mc -> TargetInfoRenderer.lastDrawn()) == null, "show_blocks off");
        set(s -> s.with("show_blocks", true));
        place("beehive[honey_level=4]");
        set(s -> s.with("show_block_details", false));
        check(drawn().lines().isEmpty(), "show_block_details off");
        set(s -> s.with("show_block_details", true));
        place("air");

        server.runCommand("summon wolf 0.5 100 2.5 {NoAI:1b,Silent:1b}");
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
            for (Wolf wolf : s.overworld().getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(8))) {
                wolf.tame(player);
            }
        });
        ctx.waitTicks(5);
        set(s -> s.with("show_health", false));
        check(drawn().health() == null, "show_health off");
        set(s -> s.with("show_owner", false));
        check(drawn().lines().isEmpty(), "show_owner off");
        set(s -> s.with("show_health", true).with("show_owner", true).with("show_entities", false));
        check(ctx.computeOnClient(mc -> TargetInfoRenderer.lastDrawn()) == null, "show_entities off");
        set(s -> s.with("show_entities", true));
        check(drawn() != null, "entities back");
        server.runCommand("tp @e[type=wolf] 0 -200 0");
        server.runCommand("kill @e[type=wolf]");
        ctx.waitTicks(3);

        place("stone");
        setEnabled(false);
        check(ctx.computeOnClient(mc -> TargetInfoRenderer.lastDrawn()) == null, "disabled: nothing");
        shot("25-disabled");
        setEnabled(true);
        check(drawn() != null, "enabled again");
    }

    // ---- Helpers ----

    private void lookAtTarget() {
        // Eye height 1.62: yaw 0 faces south; pitch 29 aims at the centre of the block two metres ahead.
        server.runCommand("tp @a 0.5 100 0.5 0 29");
        ctx.waitTicks(3);
    }

    private void place(String block) {
        server.runCommand("setblock 0 100 2 " + block);
        ctx.waitTicks(3);
    }

    private void hold(ItemStack stack) {
        String item = stack.isEmpty() ? "air" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        server.runCommand("item replace entity @a weapon.mainhand with " + item);
        ctx.waitTicks(3);
    }

    private TargetView drawn() {
        ctx.waitTicks(1);
        TargetView view = ctx.computeOnClient(mc -> TargetInfoRenderer.lastDrawn());
        if (view == null) {
            LOGGER.error("Target Info: nothing drawn; hit={} frames={} settings={} screen={}",
                    ctx.computeOnClient(mc -> String.valueOf(mc.hitResult == null ? null : mc.hitResult.getType())
                            + " " + mc.hitResult.getLocation()),
                    ctx.computeOnClient(mc -> TargetInfoRenderer.frames()),
                    ctx.computeOnClient(mc -> String.valueOf(
                            io.github.brener_fregulia.qolunified.feature.targetinfo.TargetInfoFeature.activeSettings())),
                    ctx.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
        }
        check(view != null, "an overlay is drawn");
        return view;
    }

    private int[] bounds() {
        drawn();
        return ctx.computeOnClient(mc -> TargetInfoRenderer.lastBounds());
    }

    private void expect(String name, String source, List<net.minecraft.world.item.Item> tools, Mark mark) {
        TargetView v = drawn();
        log(name, v);
        check(v.name().getString().equals(name), "name " + name + ": " + v.name().getString());
        check(v.source() != null && v.source().getString().equals(source), name + " source");
        check(toolItems(v).equals(tools), name + " tools " + toolItems(v));
        check(v.mark() == mark, name + " mark " + v.mark());
    }

    private void expectLine(String text) {
        TargetView v = drawn();
        check(v.lines().stream().map(Component::getString).anyMatch(text::equals),
                "line \"" + text + "\" in " + v.name().getString() + " "
                        + v.lines().stream().map(Component::getString).toList());
    }

    private void expectHealth(String text) {
        TargetView v = drawn();
        String actual = Component.translatable("qol_unified.target_info.health", v.health().current(),
                v.health().max()).getString();
        check(actual.equals(text), "health " + actual);
    }

    private String stateValue(String property) {
        return ctx.computeOnClient(mc -> {
            BlockState state = mc.level.getBlockState(TARGET);
            return state.getProperties().stream().filter(p -> p.getName().equals(property)).findFirst()
                    .map(p -> String.valueOf(state.getValue(p))).orElse("?");
        });
    }

    private static List<net.minecraft.world.item.Item> toolItems(TargetView v) {
        return v.tools().stream().map(ItemStack::getItem).toList();
    }

    private void log(String what, TargetView v) {
        LOGGER.info("Target Info {}: name={} source={} tools={} mark={} lines={}", what,
                v.name().getString(), v.source() == null ? null : v.source().getString(), toolItems(v), v.mark(),
                v.lines().stream().map(Component::getString).toList());
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
        // Advancement and recipe toasts would cover the right of the panel; they are not under test.
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTicks(2);
        ctx.takeScreenshot("targetinfo-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
