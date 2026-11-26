package unified.qol.dev;

import unified.qol.QolUnifiedClient;
import unified.qol.core.CoreRuntime;
import unified.qol.core.FeatureState;
import unified.qol.core.SettingsSnapshot;
import unified.qol.core.config.FeatureChoice;
import unified.qol.feature.foodhud.FoodHudFeature;
import unified.qol.feature.foodhud.FoodHudRenderer;
import unified.qol.feature.foodhud.FoodTooltip;
import unified.qol.feature.foodhud.HeldFood;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only client game test for Food HUD. Uses a disposable singleplayer world and a disposable dedicated
 * server inside {@code build/run-gametest-foodhud}; it never touches a real installation or world. Screenshots are
 * saved for visual review.
 */
final class FoodHudGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified_dev");
    private static final Identifier DEBUG_ENTRY = Identifier.fromNamespaceAndPath("qol_unified", "food_hud");

    private final ClientGameTestContext ctx;
    private TestServerContext server;

    FoodHudGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        on(core -> check(core.lifecycle().state("food_hud") == FeatureState.ACTIVE, "enabled by default"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty easy");
            server.runCommand("time set noon");
            singleplayer();
        }
        // The dedicated server only starts once the Minecraft EULA is accepted in this game directory. Accepting
        // it is the developer's decision, so the test never writes eula.txt itself.
        if (!eulaAccepted()) {
            LOGGER.warn("Food HUD game test: dedicated-server check skipped; accept the Minecraft EULA in {} to run it",
                    FabricLoader.getInstance().getGameDir().resolve("eula.txt"));
            return;
        }
        try (TestDedicatedServerContext dedicated = ctx.worldBuilder().createServer();
                var connection = dedicated.connect()) {
            server = dedicated;
            connection.waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty easy");
            dedicatedServer();
        }
    }

    /**
     * FH-AC-15 / FH-AC-16, with AppleSkin loaded: Food HUD starts disabled by the conflict, and the same situations
     * as {@link #singleplayer()} are captured with AppleSkin drawing, for side-by-side review. An explicit enable then
     * wins over the conflict default.
     */
    void compareWithAppleSkin() {
        check(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("appleskin"), "AppleSkin loaded");
        on(core -> {
            check(core.lifecycle().state("food_hud") == FeatureState.INACTIVE, "conflict default disables");
            check(core.compatibility("food_hud").defaultSuppressed(), "suppressed by AppleSkin");
        });
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty easy");
            server.runCommand("time set noon");
            setFood(20, 5.0f, 20.0f);
            hold(ItemStack.EMPTY, ItemStack.EMPTY);
            shot("01-saturation-5");
            setFood(10, 0.0f, 20.0f);
            hold(new ItemStack(Items.BREAD), ItemStack.EMPTY);
            burst("02-bread-preview");
            setFood(16, 0.0f, 8.0f);
            hold(new ItemStack(Items.COOKED_BEEF), ItemStack.EMPTY);
            burst("03-steak-health-preview");
            setFood(20, 0.0f, 20.0f);
            hold(new ItemStack(Items.GOLDEN_APPLE), ItemStack.EMPTY);
            burst("04-golden-apple-full");
            hold(new ItemStack(Items.BREAD), new ItemStack(Items.GOLDEN_APPLE));
            burst("05-full-bread-offhand-apple");
            server.runCommand("effect give @a hunger 30 0 true");
            hold(new ItemStack(Items.BREAD), ItemStack.EMPTY);
            setFood(6, 0.0f, 20.0f);
            burst("06-hunger-effect-shaking");
            server.runCommand("effect clear @a");
            hoverHotbarSlot(new ItemStack(Items.BREAD));
            shot("07-tooltip-always");
            hoverHotbarSlot(new ItemStack(Items.ROTTEN_FLESH));
            shot("07b-tooltip-rotten-flesh");
            ctx.setScreen(() -> null);
            setFood(14, 3.5f, 20.0f);
            ctx.runOnClient(mc -> {
                mc.debugEntries.setStatus(Identifier.fromNamespaceAndPath("appleskin", "food_stats"),
                        DebugScreenEntryStatus.IN_OVERLAY);
                mc.debugEntries.setOverlayVisible(true);
            });
            shot("09-f3");
            ctx.runOnClient(mc -> mc.debugEntries.setOverlayVisible(false));

            // FH-AC-16: the player's explicit enable wins over the conflict default.
            setEnabled(true);
            on(core -> check(core.choices().get("food_hud").explicitEnabled(), "explicit choice recorded"));
            shot("20-both-enabled");
        }
    }

    private void singleplayer() {
        // FH-AC-1: food 20, saturation 5 covers 2.5 icons; no held food.
        setFood(20, 5.0f, 20.0f);
        hold(ItemStack.EMPTY, ItemStack.EMPTY);
        long frames = foodFrames();
        shot("01-saturation-5");
        check(foodFrames() > frames, "hunger overlay drawn in Survival (Mixin M-1)");

        // FH-AC-2: bread at food 10, saturation 0 previews food 15, saturation 6.
        setFood(10, 0.0f, 20.0f);
        hold(new ItemStack(Items.BREAD), ItemStack.EMPTY);
        check(previewed() == Items.BREAD.components().get(net.minecraft.core.component.DataComponents.FOOD),
                "bread previewed");
        burst("02-bread-preview");

        // Hunger preview with vanilla half shanks, at even and odd hunger (food-hud-v1.md §11.1b).
        hold(new ItemStack(Items.BEETROOT), ItemStack.EMPTY);
        setFood(10, 0.0f, 20.0f);
        burst("02b-even10-beetroot-half");
        setFood(11, 0.0f, 20.0f);
        burst("02c-odd11-beetroot-completes-half");
        hold(new ItemStack(Items.BREAD), ItemStack.EMPTY);
        burst("02d-odd11-bread");
        setFood(19, 0.0f, 20.0f);
        burst("02e-odd19-bread-clamped");

        // FH-AC-2 steak and FH-AC-4: steak at food 16 previews food 20 and an estimated health gain.
        setFood(16, 0.0f, 8.0f);
        hold(new ItemStack(Items.COOKED_BEEF), ItemStack.EMPTY);
        long hearts = heartFrames();
        burst("03-steak-health-preview");
        check(heartFrames() > hearts, "heart overlay drawn (Mixin M-2)");

        // FH-AC-3: normal food at full food shows no preview; a golden apple does.
        setFood(20, 0.0f, 20.0f);
        check(previewed() == null, "full food: steak not previewed");
        hold(new ItemStack(Items.GOLDEN_APPLE), ItemStack.EMPTY);
        check(previewed() != null, "golden apple previewed at full food");
        burst("04-golden-apple-full");

        // FH-AC-5 / D-4: main-hand food that cannot be eaten passes to the off-hand.
        hold(new ItemStack(Items.BREAD), new ItemStack(Items.GOLDEN_APPLE));
        check(isPreviewed(Items.GOLDEN_APPLE), "off-hand golden apple behind full-food bread");
        burst("05-full-bread-offhand-apple");
        setFood(10, 0.0f, 20.0f);
        check(isPreviewed(Items.BREAD), "edible main-hand bread wins");
        hold(ItemStack.EMPTY, new ItemStack(Items.BREAD));
        check(isPreviewed(Items.BREAD), "off-hand bread with an empty main hand");
        hold(new ItemStack(Items.BOW), new ItemStack(Items.BREAD));
        check(previewed() == null, "a bow in the main hand stops the off-hand");
        hold(new ItemStack(Items.POTION), new ItemStack(Items.BREAD));
        check(previewed() == null, "a potion in the main hand stops the off-hand");
        hold(ItemStack.EMPTY, new ItemStack(Items.BREAD));
        apply(s -> s.with("offhand_preview", false));
        check(previewed() == null, "offhand_preview off");
        shot("05-offhand-disabled");
        apply(s -> s.with("offhand_preview", true));

        // Hunger effect: vanilla hunger sprites and the preview still line up. Saturation 0 shakes the icons.
        server.runCommand("effect give @a hunger 30 0 true");
        hold(new ItemStack(Items.BREAD), ItemStack.EMPTY);
        setFood(6, 0.0f, 20.0f);
        burst("06-hunger-effect-shaking");
        server.runCommand("effect clear @a");

        // FH-AC-6: tooltip modes, through the real getTooltipImage path (Mixin M-3).
        ItemStack bread = new ItemStack(Items.BREAD);
        check(ctx.computeOnClient(mc -> bread.getTooltipImage().orElse(null)) instanceof FoodTooltip,
                "always: food tooltip image");
        check(ctx.computeOnClient(mc -> ClientTooltipComponent.create(bread.getTooltipImage().orElseThrow())
                .getClass().getSimpleName()).equals("FoodTooltipComponent"), "Fabric callback draws the icons");
        check(ctx.computeOnClient(mc -> new ItemStack(Items.STICK).getTooltipImage().isEmpty()), "non-food: none");
        hoverHotbarSlot(new ItemStack(Items.ROTTEN_FLESH));
        shot("07b-tooltip-rotten-flesh");
        hoverHotbarSlot(bread);
        shot("07-tooltip-always");
        apply(s -> s.with("tooltip", "shift"));
        check(ctx.computeOnClient(mc -> bread.getTooltipImage().isEmpty()), "shift: hidden without Shift");
        ctx.getInput().holdShift();
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> bread.getTooltipImage().isPresent()), "shift: shown with Shift");
        shot("08-tooltip-shift-held");
        ctx.getInput().releaseShift();
        apply(s -> s.with("tooltip", "off"));
        check(ctx.computeOnClient(mc -> bread.getTooltipImage().isEmpty()), "off: never");
        apply(s -> s.with("tooltip", "always"));
        tooltipGallery();
        ctx.setScreen(() -> null);

        // FH-AC-8: F3 line (the vanilla entry status is set only in this disposable game directory).
        setFood(14, 3.5f, 20.0f);
        ctx.runOnClient(mc -> {
            mc.debugEntries.setStatus(DEBUG_ENTRY, DebugScreenEntryStatus.IN_OVERLAY);
            mc.debugEntries.setOverlayVisible(true);
        });
        shot("09-f3");
        ctx.runOnClient(mc -> mc.debugEntries.setOverlayVisible(false));

        // FH-AC-11: GUI scales 1 and 4, with armor and absorption.
        server.runCommand("item replace entity @a armor.chest with iron_chestplate");
        server.runCommand("effect give @a absorption 60 1 true");
        hold(new ItemStack(Items.COOKED_BEEF), ItemStack.EMPTY);
        setFood(12, 2.0f, 9.0f);
        for (int scale : new int[] {1, 4, 2}) {
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(scale);
                mc.resizeGui();
            });
            shot("10-gui-scale-" + scale);
        }
        server.runCommand("effect clear @a");

        // FH-AC-10 / FH-AC-14: each setting off, then the feature off, takes effect at once with no overlay pass.
        apply(s -> s.with("saturation_overlay", false).with("food_preview", false));
        shot("11-overlays-off");
        apply(s -> s.with("saturation_overlay", true).with("food_preview", true));
        setEnabled(false);
        frames = foodFrames();
        shot("12-feature-disabled");
        check(foodFrames() == frames, "no overlay pass while disabled");
        check(ctx.computeOnClient(mc -> bread.getTooltipImage().isEmpty()), "no tooltip image while disabled");
        setEnabled(true);
        frames = foodFrames();
        ctx.waitTicks(3);
        check(foodFrames() > frames, "overlay back after re-enabling, without a restart");

        // FH-AC-9: Creative and Spectator hide the bars, so no overlay pass runs; tooltips still work.
        for (String mode : new String[] {"creative", "spectator"}) {
            server.runCommand("gamemode " + mode + " @a");
            ctx.waitTicks(5);
            frames = foodFrames();
            shot("13-" + mode);
            check(foodFrames() == frames, mode + ": no hunger overlay");
        }
        check(ctx.computeOnClient(mc -> bread.getTooltipImage().isPresent()), "tooltip in Creative");
        server.runCommand("gamemode survival @a");
    }

    /** Tooltip values next to the icons, for several foods, both languages and GUI scales 1 and 2. */
    private void tooltipGallery() {
        java.util.List<ItemStack> foods = new java.util.ArrayList<>(java.util.List.of(new ItemStack(Items.BREAD),
                new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.BEETROOT), new ItemStack(Items.MELON_SLICE),
                new ItemStack(Items.GOLDEN_CARROT)));
        java.util.Map<ItemStack, String> customNames = new java.util.IdentityHashMap<>();
        for (float saturation : new float[] {0.3f, 0.4f, 1.0f, 1.2f, 1.8f}) {
            ItemStack custom = new ItemStack(Items.APPLE);
            custom.set(net.minecraft.core.component.DataComponents.FOOD, new FoodProperties(7, saturation, false));
            customNames.put(custom, "-custom-sat" + String.valueOf(saturation).replace('.', '_'));
            foods.add(custom);
        }
        for (String language : new String[] {"en_us", "pt_br"}) {
            setLanguage(language);
            for (int scale : new int[] {2, 1}) {
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeGui();
                });
                for (ItemStack food : foods) {
                    hoverHotbarSlot(food);
                    shot("08b-tooltip-" + language + "-scale" + scale + "-"
                            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(food.getItem()).getPath()
                            + customNames.getOrDefault(food, ""));
                }
            }
        }
        setLanguage("en_us");
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
    }

    private void setLanguage(String code) {
        ctx.runOnClient(mc -> {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
        });
        java.util.concurrent.CompletableFuture<Void> reload = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
        ctx.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 1200);
        ctx.waitTicks(5);
    }

    private void dedicatedServer() {
        // FH-AC-13: a dedicated server without Unified: QoL (client-only mods are not loaded there).
        setFood(12, 4.0f, 10.0f);
        hold(new ItemStack(Items.COOKED_BEEF), ItemStack.EMPTY);
        long frames = foodFrames();
        shot("14-dedicated-server");
        check(foodFrames() > frames, "overlay works on a dedicated server");
        check(ctx.computeOnClient(mc -> mc.player.getFoodData().getFoodLevel()) == 12, "food synchronized");
        check(ctx.computeOnClient(mc -> mc.player.connection.getConnection().isConnected()), "still connected");
    }

    // ---- helpers ----

    private static boolean eulaAccepted() {
        Path eula = FabricLoader.getInstance().getGameDir().resolve("eula.txt");
        try {
            return Files.exists(eula) && Files.readAllLines(eula).stream().anyMatch(l -> l.trim().equals("eula=true"));
        } catch (IOException e) {
            return false;
        }
    }

    private void setFood(int food, float saturation, float health) {
        server.runOnServer(s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.getFoodData().setFoodLevel(food);
                player.getFoodData().setSaturation(saturation);
                player.setHealth(health);
            }
        });
        ctx.waitFor(mc -> mc.player != null && mc.player.getFoodData().getFoodLevel() == food
                && Math.abs(mc.player.getFoodData().getSaturationLevel() - saturation) < 0.01f, 100);
        ctx.waitTicks(2);
    }

    private void hold(ItemStack main, ItemStack off) {
        server.runOnServer(s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, main.copy());
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, off.copy());
            }
        });
        ctx.waitFor(mc -> mc.player != null
                && ItemStack.isSameItem(mc.player.getMainHandItem(), main)
                && ItemStack.isSameItem(mc.player.getOffhandItem(), off), 100);
        ctx.waitTicks(2);
    }

    private FoodProperties previewed() {
        return ctx.computeOnClient(mc -> HeldFood.previewed(mc.player,
                FoodHudFeature.activeSettings().getBoolean("offhand_preview")));
    }

    private boolean isPreviewed(net.minecraft.world.item.Item item) {
        FoodProperties food = previewed();
        return food != null && food.equals(item.components().get(net.minecraft.core.component.DataComponents.FOOD));
    }

    private void hoverHotbarSlot(ItemStack stack) {
        hold(stack, ItemStack.EMPTY);
        ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
        ctx.waitForScreen(InventoryScreen.class);
        // Survival inventory: 176x166, first hotbar slot at (8, 142); hover its centre.
        double[] pos = ctx.computeOnClient(mc -> {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            double scale = mc.getWindow().getGuiScale();
            return new double[] {((w - 176) / 2 + 8 + 8) * scale, ((h - 166) / 2 + 142 + 8) * scale};
        });
        ctx.getInput().setCursorPos(pos[0], pos[1]);
        ctx.waitTicks(2);
    }

    private void apply(UnaryOperator<SettingsSnapshot> change) {
        on(core -> {
            FeatureChoice choice = core.choices().get("food_hud");
            core.saveAndApply(Map.of("food_hud", choice.withSettings(change.apply(choice.settings()))));
        });
        ctx.waitTicks(2);
    }

    private void setEnabled(boolean enabled) {
        on(core -> core.saveAndApply(Map.of("food_hud", core.choices().get("food_hud").withEnabled(enabled))));
        on(core -> check(core.lifecycle().state("food_hud")
                == (enabled ? FeatureState.ACTIVE : FeatureState.INACTIVE), "state after toggle"));
        ctx.waitTicks(2);
    }

    private long foodFrames() {
        return ctx.computeOnClient(mc -> FoodHudRenderer.foodOverlayFrames());
    }

    private long heartFrames() {
        return ctx.computeOnClient(mc -> FoodHudRenderer.heartOverlayFrames());
    }

    private void on(java.util.function.Consumer<CoreRuntime> assertion) {
        ctx.runOnClient(mc -> assertion.accept(QolUnifiedClient.core()));
    }

    /** Previews blink, so take a few frames across one blink period for visual review. */
    private void burst(String name) {
        for (int i = 0; i < 4; i++) {
            shot(name + "-" + (char) ('a' + i));
            ctx.waitTicks(4);
        }
    }

    private void shot(String name) {
        ctx.waitTicks(3);
        ctx.takeScreenshot("foodhud-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
