package unified.settings.runtimetest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unified.settings.UnifiedSettingsClient;
import unified.settings.registry.ModuleEntry;
import unified.settings.registry.ProviderRegistry;
import unified.settings.ui.UnifiedSettingsScreen;

/**
 * Runtime checks for M2.1.1 with the real production JARs (docs/architecture/unified-settings-integration.md §19):
 * one library copy, one U key, one Options button at every GUI scale, module order, navigation and return paths.
 * Scenario from the {@code unified_settings.test.scenario} property: QolOnly, OptimizationsOnly or Both.
 */
public final class SettingsRuntimeGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("unified_settings_runtime_test");
    private static final String QOL = "qol_unified";
    private static final String OPTIMIZATIONS = "unified_optimizations";

    private ClientGameTestContext ctx;
    private boolean qol;
    private boolean optimizations;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        String scenario = System.getProperty("unified_settings.test.scenario", "?");
        qol = !scenario.equals("OptimizationsOnly");
        optimizations = !scenario.equals("QolOnly");
        ctx.waitForScreen(TitleScreen.class);
        loadedMods();
        keyMappings();
        optionsButtonAtEveryScale(scenario);
        navigation(scenario);
        keyInWorld();
        LOGGER.info("[settings-runtime] {} passed", scenario);
    }

    /** Exactly one unified_settings, version 1.0.0, nested in an installed Unified mod; only the expected mods. */
    private void loadedMods() {
        ctx.runOnClient(mc -> {
            List<ModContainer> libs = FabricLoader.getInstance().getAllMods().stream()
                    .filter(m -> m.getMetadata().getId().equals("unified_settings")).toList();
            check(libs.size() == 1, "one unified_settings, found " + libs.size());
            ModContainer lib = libs.getFirst();
            check(lib.getMetadata().getVersion().getFriendlyString().equals("1.0.0"), "library 1.0.0");
            String parent = lib.getContainingMod().map(m -> m.getMetadata().getId()).orElse("-");
            check(parent.equals(QOL) || parent.equals(OPTIMIZATIONS), "nested in a Unified mod, was " + parent);
            check(FabricLoader.getInstance().isModLoaded(QOL) == qol, "QoL installed: " + qol);
            check(FabricLoader.getInstance().isModLoaded(OPTIMIZATIONS) == optimizations,
                    "Optimizations installed: " + optimizations);
            log("library nested in", parent);
        });
    }

    /** One shared key on physical U; QoL's legacy key only while QoL is installed (M2.1.2 migrates it). */
    private void keyMappings() {
        ctx.runOnClient(mc -> {
            KeyMapping[] keys = mc.options.keyMappings;
            check(count(keys, UnifiedSettingsClient.KEY_OPEN) == 1, "one key.unified_settings.open");
            KeyMapping open = UnifiedSettingsClient.getOpenKey();
            check(open != null && open.getDefaultKey().getValue() == InputConstants.KEY_U, "default physical U");
            check(count(keys, "key.qol_unified.open_settings") == (qol ? 1 : 0), "legacy QoL key only with QoL");
            long uDefaults = Arrays.stream(keys).filter(k -> k.getDefaultKey().getValue() == InputConstants.KEY_U)
                    .count();
            check(uDefaults == 1, "no other default U binding, found " + uDefaults);
        });
    }

    private static long count(KeyMapping[] keys, String name) {
        return Arrays.stream(keys).filter(k -> k.getName().equals(name)).count();
    }

    /** One button, overlapping nothing, at GUI scales 1–4 and two window sizes; also after resizes. */
    private void optionsButtonAtEveryScale(String scenario) {
        int[][] windows = {{854, 480}, {1920, 1080}};
        for (int[] window : windows) {
            ctx.getInput().resizeWindow(window[0], window[1]);
            for (int scale = 1; scale <= 4; scale++) {
                int s = scale;
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(s);
                    mc.resizeGui();
                });
                ctx.waitTicks(1);
                if ((int) (double) ctx.computeOnClient(mc -> mc.getWindow().getGuiScale()) != s) {
                    continue;
                }
                openOptions();
                ctx.runOnClient(mc -> {
                    Screen screen = mc.gui.screen();
                    List<AbstractWidget> widgets = Screens.getWidgets(screen);
                    List<AbstractWidget> ours = widgets.stream().filter(UnifiedSettingsClient::isOptionsButton)
                            .toList();
                    check(ours.size() == 1, "one Options button at scale " + s + ", found " + ours.size());
                    AbstractWidget button = ours.getFirst();
                    for (AbstractWidget other : widgets) {
                        if (other != button) {
                            check(!overlaps(button, other), "button overlaps " + other.getMessage().getString()
                                    + " at " + window[0] + "x" + window[1] + " scale " + s);
                        }
                    }
                    check(button.getX() >= 0 && button.getRight() <= screen.width && button.getY() >= 0
                            && button.getBottom() <= screen.height, "inside the screen");
                });
                ctx.takeScreenshot("settings-runtime-" + scenario + "-options-" + window[0] + "x" + window[1]
                        + "-gui" + s);
            }
        }
        ctx.getInput().resizeWindow(854, 480);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        ctx.waitTicks(1);
    }

    private static boolean overlaps(AbstractWidget a, AbstractWidget b) {
        return a.getX() < b.getRight() && b.getX() < a.getRight() && a.getY() < b.getBottom()
                && b.getY() < a.getBottom();
    }

    /** Options → Unified Settings → module → back again, through real clicks. */
    private void navigation(String scenario) {
        openOptions();
        click(UnifiedSettingsClient::isOptionsButton);
        ctx.waitForScreen(UnifiedSettingsScreen.class);
        ctx.runOnClient(mc -> {
            List<String> order = ProviderRegistry.modules().stream().map(ModuleEntry::getModId).toList();
            List<String> expected = new ArrayList<>();
            if (qol) {
                expected.add(QOL);
            }
            if (optimizations) {
                expected.add(OPTIMIZATIONS);
            }
            check(order.equals(expected), "modules " + order + ", expected " + expected);
            check(ProviderRegistry.modules().stream().allMatch(ModuleEntry::getAvailable), "no failed provider");
        });
        ctx.takeScreenshot("settings-runtime-" + scenario + "-shell");
        if (optimizations) {
            click(w -> key(w).equals("modmenu.nameTranslation.unified_optimizations"));
            ctx.waitTicks(2);
            ctx.runOnClient(mc -> {
                check(mc.gui.screen() instanceof UnifiedSettingsScreen, "stays on the shell");
                String shown = ((UnifiedSettingsScreen) mc.gui.screen()).message().getContents()
                        instanceof TranslatableContents t ? t.getKey() : "";
                check(shown.equals("unified_settings.no_settings"), "explains there is nothing yet: " + shown);
            });
            ctx.takeScreenshot("settings-runtime-" + scenario + "-optimizations-empty");
        }
        if (qol) {
            click(w -> key(w).equals("modmenu.nameTranslation.qol_unified"));
            ctx.waitTicks(2);
            ctx.runOnClient(mc -> check(mc.gui.screen().getClass().getName()
                    .equals("unified.qol.ui.screen.SettingsScreen"), "QoL workspace opens"));
            ctx.takeScreenshot("settings-runtime-" + scenario + "-qol-workspace");
            ctx.runOnClient(mc -> mc.gui.screen().onClose()); // QoL's Back
            ctx.waitTicks(2);
            ctx.runOnClient(mc -> check(mc.gui.screen() instanceof UnifiedSettingsScreen, "back to the shell"));
        }
        ctx.runOnClient(mc -> mc.gui.screen().onClose()); // the shell's Back
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> check(mc.gui.screen() instanceof OptionsScreen, "back to Options"));
        ctx.setScreen(TitleScreen::new);
        ctx.waitTicks(2);
    }

    /** The physical U key opens Unified Settings in game; Escape returns to the game. */
    private void keyInWorld() {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_U));
            ctx.waitForScreen(UnifiedSettingsScreen.class);
            ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
            ctx.waitTicks(2);
            ctx.runOnClient(mc -> check(mc.gui.screen() == null, "Escape returns to the game"));
        }
    }

    private void openOptions() {
        ctx.setScreen(() -> new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options));
        ctx.waitForScreen(OptionsScreen.class);
        ctx.waitTicks(1);
    }

    private void click(Predicate<AbstractWidget> which) {
        double[] center = ctx.computeOnClient(mc -> {
            AbstractWidget w = Screens.getWidgets(mc.gui.screen()).stream().filter(which).findFirst()
                    .orElseThrow(() -> new AssertionError("widget not found"));
            double scale = mc.getWindow().getGuiScale();
            return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
        });
        ctx.getInput().setCursorPos(center[0], center[1]);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(1);
    }

    private static String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static void log(String what, Object value) {
        LOGGER.info("[settings-runtime] {}: {}", what, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
