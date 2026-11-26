package io.github.brener_fregulia.qolunified.dev;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.QolUnifiedClient;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapScreen;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.KeyboardMapTexts;
import io.github.brener_fregulia.qolunified.ui.FeatureComparisons;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import io.github.brener_fregulia.qolunified.ui.screen.FeatureSwitch;
import io.github.brener_fregulia.qolunified.ui.screen.SettingsScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Development-only client game test for the unified settings workspace (settings-ux-v1.md), in the disposable
 * {@code build/run-gametest-settings} directory. Drives the real screen with the simulated mouse; keyboard handling is
 * exercised through the widgets' own key handlers, because simulated keys do not reach every screen here.
 */
final class SettingsWorkspaceGameTest {
    private static final String[] FEATURES = {"food_hud", "target_info", "shulker_preview", "keyboard_map",
        "chat_heads", "sound_control"};

    private final ClientGameTestContext ctx;

    SettingsWorkspaceGameTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    void run() {
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        navigation();
        switchAutoApply();
        saveFailure();
        keyboard();
        search();
        tools();
        commit();
        sizes();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode survival @a");
            comparisonsInWorld();
            foodHudOffInWorld();
        }
        ctx.setScreen(TitleScreen::new);
    }

    /** Second launch (scenario "settingsrestart"): the Food HUD switched OFF in the previous run is still OFF. */
    void afterRestart() {
        check(!QolUnifiedClient.core().choices().get("food_hud").enabled(), "Food HUD OFF survived the restart");
        check(QolUnifiedClient.core().lifecycle().state("food_hud").name().equals("INACTIVE"), "and is inactive");
        open();
        check(locate(UiKeys.SWITCH_OFF).isPresent(), "the switch shows OFF after restart");
        shot("30-after-restart");
        click(UiKeys.SWITCH_OFF);
        check(QolUnifiedClient.core().choices().get("food_hud").enabled(), "re-enabled for later runs");
        back();
    }

    // ---- In a world: Food HUD OFF removes the HUD at once, and reopening shows OFF ----

    private void foodHudOffInWorld() {
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        long before = ctx.computeOnClient(mc ->
                io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudRenderer.foodOverlayFrames());
        ctx.waitTicks(5);
        check(ctx.computeOnClient(mc -> io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudRenderer
                .foodOverlayFrames()) > before, "Food HUD draws while ON");
        open();
        selectFeature("food_hud");
        click(UiKeys.SWITCH_ON);
        check(QolUnifiedClient.core().lifecycle().state("food_hud").name().equals("INACTIVE"),
                "Food HUD OFF applied without Done");
        check(config().contains("food_hud"), "and saved");
        back();
        ctx.waitTicks(5);
        long off = ctx.computeOnClient(mc ->
                io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudRenderer.foodOverlayFrames());
        ctx.waitTicks(5);
        check(ctx.computeOnClient(mc -> io.github.brener_fregulia.qolunified.feature.foodhud.FoodHudRenderer
                .foodOverlayFrames()) == off, "the HUD overlay is gone");
        shot("23-food-hud-off-in-world");
        open();
        selectFeature("food_hud");
        check(locate(UiKeys.SWITCH_OFF).isPresent(), "reopened: still OFF");
        back();
        // Left OFF on purpose: the "settingsrestart" scenario checks it after a real restart.
    }

    // ---- Persistence failure: nothing applied, Retry and Undo ----

    private void saveFailure() {
        Path blocker = configFile().resolveSibling("config.json.tmp");
        open();
        selectFeature("chat_heads");
        block(blocker, true); // the atomic write's temporary file cannot be created
        byte[] before = configBytes();
        click(UiKeys.SWITCH_ON);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).saveFailed()), "failure reported");
        check(QolUnifiedClient.core().lifecycle().state("chat_heads").name().equals("ACTIVE"),
                "nothing applied after a failed save");
        check(java.util.Arrays.equals(before, configBytes()), "file untouched");
        shot("09-save-failed");
        click(UiKeys.RETRY_SAVE);
        check(ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).saveFailed()), "retry fails while blocked");
        block(blocker, false);
        click(UiKeys.RETRY_SAVE);
        check(!ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).saveFailed())
                && QolUnifiedClient.core().lifecycle().state("chat_heads").name().equals("INACTIVE"),
                "retry saves, then applies");
        // Undo: a failed change is dropped and the controls show what is stored.
        block(blocker, true);
        click(UiKeys.SWITCH_OFF);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).saveFailed()), "second failure");
        click(UiKeys.UNDO_CHANGE);
        check(locate(UiKeys.SWITCH_OFF).isPresent() && !QolUnifiedClient.core().choices().get("chat_heads").enabled(),
                "undo shows the stored OFF");
        block(blocker, false);
        click(UiKeys.SWITCH_OFF);
        check(QolUnifiedClient.core().lifecycle().state("chat_heads").name().equals("ACTIVE"), "back ON");
        back();
    }

    private static void block(Path path, boolean blocked) {
        try {
            // A non-empty directory where the temporary file goes: it can be neither created nor deleted.
            Path inside = path.resolve("keep");
            if (blocked) {
                Files.createDirectories(path);
                Files.writeString(inside, "x");
            } else {
                Files.deleteIfExists(inside);
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    // ---- Navigation: every feature, inline settings, no second screen ----

    private void navigation() {
        open();
        check(selected().equals("food_hud"), "first feature selected");
        shot("01-food-hud");
        for (String id : FEATURES) {
            selectFeature(id);
            check(selected().equals(id), "selected " + id);
            check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof SettingsScreen), "still one screen: " + id);
            shot("02-" + id);
        }
        // Descriptor types on real features: booleans and an enum (Food HUD), a map summary (Sound Control).
        selectFeature("food_hud");
        check(locate("options.on").isPresent(), "boolean rows inline");
        check(locate(UiKeys.enumValue("food_hud", "tooltip", "always")).isPresent(), "enum row inline");
        selectFeature("sound_control");
        check(ctx.computeOnClient(mc -> textOnScreen(mc, UiKeys.MAP_ENTRIES)), "map summary row");
        // Collapsing keeps the selection; contextual entry points preselect.
        click(UiKeys.category(FeatureCategory.AUDIO));
        check(selected().equals("sound_control") && !expanded(FeatureCategory.AUDIO), "collapse keeps selection");
        back();
        ctx.setScreen(() -> new SettingsScreen(new TitleScreen(), QolUnifiedClient.core(), "shulker_preview"));
        ctx.waitForScreen(SettingsScreen.class);
        check(selected().equals("shulker_preview"), "contextual entry point preselects (Q-SU8)");
        back();
    }

    // ---- Switch: validated, saved and applied at once (settings-ux-v1.md §18) ----

    private void switchAutoApply() {
        byte[] before = configBytes();
        open();
        selectFeature("target_info");
        check(java.util.Arrays.equals(before, configBytes()), "navigation writes nothing");
        click(UiKeys.SWITCH_ON);
        check(locate(UiKeys.SWITCH_OFF).isPresent(), "switch shows OFF");
        check(QolUnifiedClient.core().lifecycle().state("target_info").name().equals("INACTIVE"),
                "applied at once");
        check(!QolUnifiedClient.core().choices().get("target_info").enabled() && config().contains("target_info"),
                "saved at once");
        check(ctx.computeOnClient(mc -> textOnScreen(mc, UiKeys.APPLIES_WHEN_ON)), "Applies when ON hint");
        shot("03-target-info-off");
        byte[] saved = configBytes();
        selectFeature("food_hud");
        selectFeature("target_info");
        check(java.util.Arrays.equals(saved, configBytes()), "no write for navigation");
        click(UiKeys.SWITCH_OFF);
        check(QolUnifiedClient.core().lifecycle().state("target_info").name().equals("ACTIVE"), "ON again at once");
        shot("03b-target-info-on");
        byte[] on = configBytes();
        back();
        check(java.util.Arrays.equals(on, configBytes()), "Back writes nothing more");
    }

    // ---- Keyboard: switch and sidebar key handlers ----

    private void keyboard() {
        open();
        selectFeature("chat_heads");
        boolean toggled = ctx.computeOnClient(mc -> {
            FeatureSwitch toggle = find(mc.gui.screen(), FeatureSwitch.class).orElseThrow();
            toggle.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            return !draft(mc).choice("chat_heads").enabled();
        });
        check(toggled && QolUnifiedClient.core().lifecycle().state("chat_heads").name().equals("INACTIVE"),
                "Enter toggles and applies");
        boolean space = ctx.computeOnClient(mc -> {
            FeatureSwitch toggle = find(mc.gui.screen(), FeatureSwitch.class).orElseThrow();
            toggle.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
            return draft(mc).choice("chat_heads").enabled();
        });
        check(space && QolUnifiedClient.core().lifecycle().state("chat_heads").name().equals("ACTIVE"),
                "Space toggles back and applies");
        // Left collapses and Right expands a focused category row, without changing the selection.
        ctx.runOnClient(mc -> categoryKey(mc, FeatureCategory.CHAT_SOCIAL, InputConstants.KEY_LEFT));
        ctx.waitTicks(2);
        check(!expanded(FeatureCategory.CHAT_SOCIAL) && selected().equals("chat_heads"), "Left collapses");
        ctx.runOnClient(mc -> categoryKey(mc, FeatureCategory.CHAT_SOCIAL, InputConstants.KEY_RIGHT));
        ctx.waitTicks(2);
        check(expanded(FeatureCategory.CHAT_SOCIAL), "Right expands");
        // Tab moves focus through the screen's widgets.
        GuiEventListener before = ctx.computeOnClient(mc -> mc.gui.screen().getFocused());
        ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_TAB));
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen().getFocused()) != before, "Tab moves focus");
        shot("04-keyboard-focus");
        back();
    }

    // ---- Search: setting results reveal the row ----

    private void search() {
        open();
        type("mining");
        shot("05-search-mining");
        check(selected().equals("target_info"), "panel follows the first match");
        click("qol_unified.feature.target_info.setting.show_mining_progress");
        check(selected().equals("target_info"), "setting result selects its feature");
        shot("06-search-revealed");
        type("conteudo"); // accent-free query for "Content icon"/"Ícone do conteúdo" works in either language
        type("content icon");
        check(selected().equals("shulker_preview"), "setting name search");
        type("predominant");
        check(selected().equals("shulker_preview"), "enum option search");
        type("zzzz");
        check(ctx.computeOnClient(mc -> textOnScreen(mc, UiKeys.NO_MATCHES)), "empty results message");
        shot("07-search-empty");
        type("");
        check(expanded(FeatureCategory.INVENTORY), "clearing keeps the chosen feature's category open");
        back();
    }

    // ---- Tools return to the same feature ----

    private void tools() {
        open();
        selectFeature("keyboard_map");
        click(KeyboardMapTexts.OPEN_TOOL);
        ctx.waitForScreen(KeyboardMapScreen.class);
        ctx.runOnClient(mc -> mc.gui.screen().onClose());
        ctx.waitForScreen(SettingsScreen.class);
        check(selected().equals("keyboard_map") && expanded(FeatureCategory.CONTROLS),
                "Keyboard Map returns to the same feature");
        shot("08-back-from-keyboard-map");
        back();
    }

    // ---- Done persists once; reopening shows the saved value ----

    private void commit() {
        open();
        selectFeature("target_info");
        click("options.on"); // first boolean: show_blocks
        check(!QolUnifiedClient.core().choices().get("target_info").settings().getBoolean("show_blocks")
                && !QolUnifiedClient.core().lifecycle().effectiveSettings("target_info").getBoolean("show_blocks"),
                "boolean saved and applied at once");
        check(config().contains("\"show_blocks\": false"), "written to the file");
        byte[] once = configBytes();
        back();
        check(java.util.Arrays.equals(once, configBytes()), "closing commits nothing more");
        open();
        selectFeature("target_info");
        click("options.off");
        back();
        check(QolUnifiedClient.core().choices().get("target_info").settings().getBoolean("show_blocks"),
                "restored");
    }

    // ---- Responsive tiers and GUI scales ----

    private void sizes() {
        // 1100×760 at scale 3 is 366 GUI px wide: the drawer tier.
        int[][] windows = {{854, 480}, {1100, 760}, {1280, 720}, {1920, 1080}, {2560, 1080}};
        for (int[] window : windows) {
            ctx.getInput().resizeWindow(window[0], window[1]);
            for (int scale = 1; scale <= 4; scale++) {
                int s = scale;
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(s);
                    mc.resizeGui();
                });
                ctx.waitTicks(2);
                if ((int) (double) ctx.computeOnClient(mc -> mc.getWindow().getGuiScale()) != s) {
                    continue;
                }
                open();
                selectFeatureSafely("food_hud");
                int width = ctx.computeOnClient(mc -> mc.gui.screen().width);
                boolean compact = ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).compact());
                check(compact == (width < 400), "tier at " + width);
                shot("10-" + window[0] + "x" + window[1] + "-gui" + s);
                if (compact) {
                    clickLiteral("☰");
                    shot("11-" + window[0] + "x" + window[1] + "-gui" + s + "-drawer");
                    click("qol_unified.feature.target_info.name");
                    check(selected().equals("target_info"), "drawer selection");
                    check(locate("qol_unified.feature.food_hud.name").isEmpty(), "drawer closes after choosing");
                    shot("12-" + window[0] + "x" + window[1] + "-gui" + s + "-after-drawer");
                }
                back();
            }
        }
        ctx.getInput().resizeWindow(854, 480);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
    }

    // ---- Comparisons in a world (items are only available there) ----

    private void comparisonsInWorld() {
        check(ctx.computeOnClient(mc -> FeatureComparisons.itemsAvailable()), "items bound in a world");
        check(ctx.computeOnClient(mc -> FeatureComparisons.find("food_hud").isPresent()
                && FeatureComparisons.find("shulker_preview").isPresent()
                && FeatureComparisons.find("target_info").isPresent()
                && FeatureComparisons.find("chat_heads").isEmpty()), "comparisons for Food HUD, Shulker, Target Info");
        open();
        shot("20-food-hud-comparison-world");
        selectFeature("shulker_preview");
        shot("21-shulker-comparison-world");
        selectFeature("target_info");
        shot("21b-target-info-comparison-world");
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(4);
            mc.resizeGui();
        });
        ctx.waitTicks(3);
        shot("22-shulker-comparison-gui4");
        ctx.getInput().resizeWindow(854, 480);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        back();
    }

    // ---- Helpers ----

    private void open() {
        ctx.setScreen(() -> new SettingsScreen(Minecraft.getInstance().gui.screen(), QolUnifiedClient.core()));
        ctx.waitForScreen(SettingsScreen.class);
        ctx.waitTicks(2);
    }

    private void back() {
        click(UiKeys.BACK);
        ctx.waitTicks(2);
    }

    private String selected() {
        return ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).navigation().selected());
    }

    private boolean expanded(FeatureCategory category) {
        return ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).navigation().isExpanded(category));
    }

    private static io.github.brener_fregulia.qolunified.ui.SettingsDraft draft(Minecraft mc) {
        return ((SettingsScreen) mc.gui.screen()).draft();
    }

    /** Clicks the feature's row, expanding its category first when needed. */
    private void selectFeature(String id) {
        String nameKey = "qol_unified.feature." + id + ".name";
        if (locate(nameKey).isEmpty()) {
            // The development fixtures add many features; collapse open categories (Left key, as a player would)
            // so the wanted category row is inside the sidebar before clicking it.
            for (FeatureCategory open : FeatureCategory.values()) {
                if (expanded(open)) {
                    ctx.runOnClient(mc -> categoryKey(mc, open, InputConstants.KEY_LEFT));
                    ctx.waitTicks(1);
                }
            }
            FeatureCategory category = QolUnifiedClient.core().registry().require(id).descriptor().category();
            // Scroll the sidebar with the wheel towards the category, like a player would.
            ctx.getInput().setCursorPos(ctx.computeOnClient(mc -> 40.0 * mc.getWindow().getGuiScale()),
                    ctx.computeOnClient(mc -> 100.0 * mc.getWindow().getGuiScale()));
            ctx.getInput().scroll(category.ordinal() >= FeatureCategory.values().length / 2 ? -20 : 20);
            ctx.waitTicks(2);
            click(UiKeys.category(category));
        }
        if (!selected().equals(id)) {
            click(nameKey);
        }
    }

    private void selectFeatureSafely(String id) {
        if (!ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).compact())) {
            selectFeature(id);
        }
    }

    private void categoryKey(Minecraft mc, FeatureCategory category, int key) {
        visit(mc.gui.screen(), w -> {
            if (w.getMessage().getContents() instanceof TranslatableContents t
                    && t.getKey().equals(UiKeys.category(category))) {
                w.keyPressed(new KeyEvent(key, 0, 0));
            }
        });
    }

    private void type(String text) {
        ctx.runOnClient(mc -> {
            Screen screen = mc.gui.screen();
            var box = (net.minecraft.client.gui.components.EditBox) screen.children().stream()
                    .filter(net.minecraft.client.gui.components.EditBox.class::isInstance).findFirst().orElseThrow();
            screen.setFocused(box);
            box.setValue(text);
        });
        ctx.waitTicks(2);
    }

    private static boolean textOnScreen(Minecraft mc, String key) {
        // Panel text rows are drawn, not widgets; the draft and navigation tell what the panel built. Here the
        // translated text is searched in the screen's narration-free text: widgets' messages and panel rows.
        var found = new boolean[1];
        visit(mc.gui.screen(), w -> {
            if (w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
                found[0] = true;
            }
        });
        return found[0] || mc.gui.screen() instanceof SettingsScreen s && s.panelShows(key);
    }

    private Optional<double[]> locate(String key) {
        return ctx.computeOnClient(mc -> {
            var found = new ArrayList<AbstractWidget>();
            visit(mc.gui.screen(), w -> {
                if (w.visible && w.isActive() && w.getMessage().getContents() instanceof TranslatableContents t
                        && t.getKey().equals(key) && w.getY() >= 0 && w.getY() < mc.gui.screen().height) {
                    found.add(w);
                }
            });
            return found.stream().findFirst().map(w -> {
                int scale = mc.getWindow().getGuiScale();
                return new double[] {(w.getX() + w.getWidth() / 2.0) * scale,
                    (w.getY() + w.getHeight() / 2.0) * scale};
            });
        });
    }

    private void click(String key) {
        double[] position = locate(key).orElseThrow(() -> new AssertionError("No active widget " + key));
        ctx.getInput().setCursorPos(position[0], position[1]);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    private void clickLiteral(String text) {
        double[] position = ctx.computeOnClient(mc -> {
            var found = new ArrayList<Button>();
            visit(mc.gui.screen(), w -> {
                if (w instanceof Button b && b.getMessage().getString().equals(text)) {
                    found.add(b);
                }
            });
            Button b = found.getFirst();
            int scale = mc.getWindow().getGuiScale();
            return new double[] {(b.getX() + b.getWidth() / 2.0) * scale, (b.getY() + b.getHeight() / 2.0) * scale};
        });
        ctx.getInput().setCursorPos(position[0], position[1]);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    private static <T> Optional<T> find(GuiEventListener root, Class<T> type) {
        var found = new ArrayList<T>();
        visit(root, w -> {
            if (type.isInstance(w)) {
                found.add(type.cast(w));
            }
        });
        return found.stream().findFirst();
    }

    private static void visit(GuiEventListener listener, Consumer<AbstractWidget> action) {
        if (listener instanceof AbstractWidget widget) {
            action.accept(widget);
        }
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : List.copyOf(container.children())) {
                visit(child, action);
            }
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("qol_unified").resolve("config.json");
    }

    private static byte[] configBytes() {
        try {
            return Files.exists(configFile()) ? Files.readAllBytes(configFile()) : new byte[0];
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String config() {
        return new String(configBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void shot(String name) {
        ctx.waitTicks(2);
        ctx.takeScreenshot("settings-" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
