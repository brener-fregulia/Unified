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
import unified.settings.ui.WorkspaceNavigation;

/**
 * Runtime checks for M2.1.1–M2.1.2 with the real production JARs (docs/architecture/unified-settings-integration.md §19):
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
        legacyKeyMigration();
        optionsButtonAtEveryScale(scenario);
        navigation(scenario);
        keyInWorld();
        LOGGER.info("[settings-runtime] {} passed", scenario);
    }

    /** Exactly one unified_settings, version 1.1.0, nested in an installed Unified mod; only the expected mods. */
    private void loadedMods() {
        ctx.runOnClient(mc -> {
            List<ModContainer> libs = FabricLoader.getInstance().getAllMods().stream()
                    .filter(m -> m.getMetadata().getId().equals("unified_settings")).toList();
            check(libs.size() == 1, "one unified_settings, found " + libs.size());
            ModContainer lib = libs.getFirst();
            check(lib.getMetadata().getVersion().getFriendlyString().equals("1.1.0"), "library 1.1.0");
            String parent = lib.getContainingMod().map(m -> m.getMetadata().getId()).orElse("-");
            check(parent.equals(QOL) || parent.equals(OPTIMIZATIONS), "nested in a Unified mod, was " + parent);
            check(FabricLoader.getInstance().isModLoaded(QOL) == qol, "QoL installed: " + qol);
            check(FabricLoader.getInstance().isModLoaded(OPTIMIZATIONS) == optimizations,
                    "Optimizations installed: " + optimizations);
            log("library nested in", parent);
        });
    }

    /** One shared key, default physical U; QoL's legacy key is gone (M2.1.2). */
    private void keyMappings() {
        ctx.runOnClient(mc -> {
            KeyMapping[] keys = mc.options.keyMappings;
            check(count(keys, UnifiedSettingsClient.KEY_OPEN) == 1, "one key.unified_settings.open");
            KeyMapping open = UnifiedSettingsClient.getOpenKey();
            check(open != null && open.getDefaultKey().getValue() == InputConstants.KEY_U, "default physical U");
            check(count(keys, "key.qol_unified.open_settings") == 0, "legacy QoL key no longer registered");
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

    /**
     * Options → Unified Settings opens the integrated workspace directly (M2.1.2): module rows only with both mods,
     * Optimizations listed without fake features, search across modules, Back returns to Options.
     */
    private void navigation(String scenario) {
        openOptions();
        click(UnifiedSettingsClient::isOptionsButton);
        ctx.waitForScreen(UnifiedSettingsScreen.class);
        ctx.waitTicks(2);
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
            UnifiedSettingsScreen screen = (UnifiedSettingsScreen) mc.gui.screen();
            List<WorkspaceNavigation.Row> rows = screen.navRows();
            List<String> moduleRows = rows.stream().filter(r -> r.getKind() == WorkspaceNavigation.Kind.MODULE)
                    .map(WorkspaceNavigation.Row::getModuleId).toList();
            check(moduleRows.equals(qol && optimizations ? List.of(QOL, OPTIMIZATIONS) : List.of()),
                    "module rows " + moduleRows);
            if (qol) {
                check("food_hud".equals(screen.navigation().selected()), "QoL's first feature selected");
                check(screen.workspace(QOL) != null, "QoL workspace attached");
                check(rows.stream().anyMatch(r -> r.getKind() == WorkspaceNavigation.Kind.CATEGORY),
                        "QoL categories shown");
            } else {
                check(rows.isEmpty(), "Optimizations only: no sidebar rows, an explanation instead");
                check(classMissing("unified.qol.ui.screen.QolWorkspace"), "no QoL classes");
            }
            if (optimizations) {
                check(screen.navigation().modules().stream().filter(m -> m.getId().equals(OPTIMIZATIONS))
                        .allMatch(m -> m.getFeatures().isEmpty()), "Optimizations shows no fake features");
                if (qol) {
                    check(rows.stream().anyMatch(r -> r.getKind() == WorkspaceNavigation.Kind.NOTE
                            && r.getModuleId().equals(OPTIMIZATIONS)), "Optimizations says it has no features yet");
                }
            }
        });
        ctx.takeScreenshot("settings-runtime-" + scenario + "-workspace");
        if (qol && optimizations) {
            // Collapse the QoL group: the Optimizations group and its note come into view.
            ctx.runOnClient(mc -> {
                UnifiedSettingsScreen screen = (UnifiedSettingsScreen) mc.gui.screen();
                screen.navigation().clickModule(QOL);
                screen.resize(screen.width, screen.height); // rebuild
                check(!screen.navigation().isModuleExpanded(QOL), "QoL group collapsed");
                check(screen.navRows().stream().anyMatch(r -> r.getKind() == WorkspaceNavigation.Kind.NOTE),
                        "Optimizations note visible");
            });
            ctx.waitTicks(1);
            ctx.takeScreenshot("settings-runtime-" + scenario + "-groups");
            ctx.runOnClient(mc -> ((UnifiedSettingsScreen) mc.gui.screen()).navigation().setModuleExpanded(QOL, true));
        }
        // Narrow GUI (drawer below 400 GUI px) and an ultrawide window (workspace centered and bounded).
        layout(scenario, 640, 480, 2, "narrow");
        layout(scenario, 2560, 1080, 2, "ultrawide");
        layout(scenario, 854, 480, 2, null);
        if (qol) {
            // Search spans the modules and lists results under their owning module.
            ctx.runOnClient(mc -> {
                Screen screen = mc.gui.screen();
                var box = (net.minecraft.client.gui.components.EditBox) screen.children().stream()
                        .filter(net.minecraft.client.gui.components.EditBox.class::isInstance).findFirst()
                        .orElseThrow();
                screen.setFocused(box);
                box.setValue("zoom");
            });
            ctx.waitTicks(2);
            ctx.runOnClient(mc -> {
                UnifiedSettingsScreen screen = (UnifiedSettingsScreen) mc.gui.screen();
                check("zoom".equals(screen.navigation().selected()), "search selects the first match");
                List<WorkspaceNavigation.Row> rows = screen.navRows();
                check(rows.stream().anyMatch(r -> r.getKind() == WorkspaceNavigation.Kind.FEATURE
                        && "zoom".equals(r.getFeatureId()) && r.getModuleId().equals(QOL)), "Zoom found under QoL");
                check(rows.stream().noneMatch(r -> r.getModuleId().equals(OPTIMIZATIONS)),
                        "modules without matches are hidden");
            });
            ctx.takeScreenshot("settings-runtime-" + scenario + "-search");
        }
        ctx.runOnClient(mc -> mc.gui.screen().onClose()); // Back
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> check(mc.gui.screen() instanceof OptionsScreen, "back to Options"));
        ctx.setScreen(TitleScreen::new);
        ctx.waitTicks(2);
    }

    /**
     * The run directory is seeded with options.txt binding QoL's former key to K (build.gradle.kts). With QoL it moves
     * to the shared key once, through Minecraft's own options save, and the legacy line is gone afterwards; without
     * QoL nothing moves.
     */
    private void legacyKeyMigration() {
        // The game-test harness restores default options in memory before the test starts, so the migrated binding is
        // checked where Minecraft saved it at client start.
        ctx.runOnClient(mc -> {
            try {
                List<String> lines = java.nio.file.Files.readAllLines(mc.gameDirectory.toPath().resolve("options.txt"));
                String shared = lines.stream().filter(l -> l.startsWith("key_key.unified_settings.open:")).findFirst()
                        .orElse("-");
                check(shared.equals("key_key.unified_settings.open:key.keyboard." + (qol ? "k" : "u")),
                        "saved shared key: " + shared);
                check(lines.stream().noneMatch(l -> l.startsWith("key_key.qol_unified.open_settings:")),
                        "legacy line dropped by Minecraft's save, so the migration runs once");
                log("saved shared key", shared);
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        });
    }

    private void layout(String scenario, int w, int h, int scale, String shot) {
        ctx.getInput().resizeWindow(w, h);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        ctx.waitTicks(2);
        if (shot != null) {
            ctx.runOnClient(mc -> check(mc.gui.screen() instanceof UnifiedSettingsScreen, "workspace after resize"));
            ctx.takeScreenshot("settings-runtime-" + scenario + "-" + shot);
        }
    }

    private static boolean classMissing(String name) {
        try {
            Class.forName(name);
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    /** The shared key, as bound now, opens Unified Settings in game; Escape returns to the game. */
    private void keyInWorld() {
        // The harness restores default bindings without rebuilding Minecraft's key lookup; rebuild it as a rebind would.
        int key = ctx.computeOnClient(mc -> {
            KeyMapping.resetMapping();
            log("in-memory shared key", UnifiedSettingsClient.getOpenKey().saveString());
            return InputConstants.getKey(UnifiedSettingsClient.getOpenKey().saveString()).getValue();
        });
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            ctx.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(key));
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
