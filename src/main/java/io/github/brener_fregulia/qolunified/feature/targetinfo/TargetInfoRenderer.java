package io.github.brener_fregulia.qolunified.feature.targetinfo;

import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.mixin.BossHealthOverlayAccessor;
import io.github.brener_fregulia.qolunified.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the Target Info panel, horizontally centered at the top and below any boss bars (target-info-v1.md §8).
 * The view is rebuilt at most once per client tick; only mining progress is read every frame.
 */
public final class TargetInfoRenderer {
    private static final int PAD = 4;
    private static final int ICON = 16;
    private static final int LINE = 10;
    private static final int HEART = 9;
    private static final int HEART_STEP = 8;
    private static final int BACKGROUND = 0xC0101018;
    private static final int BORDER = 0x50FFFFFF;
    private static final int NAME = 0xFFFFFFFF;
    private static final int SOURCE = 0xFF5555FF;
    private static final int DETAIL = 0xFFD0D0D0;
    private static final int CHECK = 0xFF55FF55;
    private static final int CROSS = 0xFFFF5555;
    private static final int PROGRESS = 0xD0FFFFFF;
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");

    private static final TargetResolver RESOLVER = new TargetResolver();
    private static TargetView view;
    private static long builtTick = Long.MIN_VALUE;
    private static Object builtTarget;
    private static SettingsSnapshot builtSettings;

    // Read by the development game tests: the last drawn panel and its bounds (render thread only).
    private static TargetView lastDrawn;
    private static int[] lastBounds = new int[4];
    private static float lastProgress = -1.0f;
    private static long lastExtractNanos;
    private static long frames;

    private TargetInfoRenderer() {
    }

    static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        SettingsSnapshot settings = TargetInfoFeature.activeSettings();
        Minecraft mc = Minecraft.getInstance();
        lastDrawn = null;
        lastProgress = -1.0f;
        lastExtractNanos = System.nanoTime();
        if (settings == null) {
            view = null;
            return; // no work at all while disabled
        }
        boolean screenOpen = mc.gui.screen() != null;
        boolean visibleAtAll = OverlayRules.visible(true, OverlayRules.Target.BLOCK, true, true,
                mc.gui.hud.isHidden(), screenOpen, mc.gui.hud.getDebugOverlay().showDebugScreen());
        if (!visibleAtAll || mc.level == null) {
            view = null;
            return;
        }
        long tick = mc.level.getGameTime();
        // Vanilla makes a new hit result every frame; the target's identity is its block position or entity.
        Object target = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? b.getBlockPos()
                : mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e ? e.getEntity() : null;
        if (tick != builtTick || !java.util.Objects.equals(target, builtTarget) || settings != builtSettings) {
            view = RESOLVER.resolve(mc, settings);
            builtTick = tick;
            builtTarget = target;
            builtSettings = settings;
        }
        if (view == null) {
            return;
        }
        float progress = -1.0f;
        if (view.pos() != null && settings.getBoolean(TargetInfoSettings.SHOW_MINING_PROGRESS)) {
            MultiPlayerGameMode gameMode = mc.gameMode;
            if (gameMode != null) {
                MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) gameMode;
                progress = OverlayRules.progress(gameMode.isDestroying(),
                        view.pos().equals(accessor.qolUnified$destroyBlockPos()),
                        gameMode.getPlayerMode().isCreative(), view.instant(), accessor.qolUnified$destroyProgress());
            }
        }
        int bossBars = ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).qolUnified$events().size();
        draw(graphics, mc.font, view, progress, OverlayRules.top(bossBars, graphics.guiHeight()));
    }

    private static void draw(GuiGraphicsExtractor g, Font font, TargetView v, float progress, int top) {
        boolean icon = !v.icon().isEmpty();
        int textX = PAD + (icon ? ICON + 4 : 0);
        int maxText = Math.max(60, g.guiWidth() * 2 / 5);
        String name = v.name().getString();
        if (font.width(name) > maxText) {
            name = font.plainSubstrByWidth(name, maxText - font.width("…")) + "…";
        }
        int textWidth = font.width(name);
        int rows = 1;
        if (v.source() != null) {
            textWidth = Math.max(textWidth, font.width(v.source()));
            rows++;
        }
        for (Component line : v.lines()) {
            textWidth = Math.max(textWidth, Math.min(maxText, font.width(line)));
            rows++;
        }
        EntityDetails.Health health = v.health();
        int heartRows = 0;
        if (health != null) {
            if (health.hearts()) {
                int containers = (health.maxHalves() + 1) / 2;
                textWidth = Math.max(textWidth, Math.max(0, containers - 1) * HEART_STEP + HEART);
                heartRows = 1;
            } else {
                Component text = healthText(health);
                textWidth = Math.max(textWidth, font.width(text));
                rows++;
            }
        }
        int right = 0;
        if (!v.tools().isEmpty()) {
            right += v.tools().size() * (ICON + 1);
        }
        if (v.mark() != ToolAdvice.Mark.NONE) {
            right += font.width("✔") + 2;
        }
        int width = textX + textWidth + (right > 0 ? 6 + right : 0) + PAD;
        int contentHeight = rows * LINE - 1 + heartRows * (HEART + 1);
        int height = PAD + Math.max(icon || right > 0 ? ICON : 0, contentHeight) + PAD;
        int x = (g.guiWidth() - width) / 2;
        int y = top;

        g.fill(x, y, x + width, y + height, BACKGROUND);
        g.fill(x, y, x + width, y + 1, BORDER);
        g.fill(x, y + height - 1, x + width, y + height, BORDER);
        g.fill(x, y + 1, x + 1, y + height - 1, BORDER);
        g.fill(x + width - 1, y + 1, x + width, y + height - 1, BORDER);

        if (icon) {
            g.item(v.icon(), x + PAD, y + (height - ICON) / 2);
        }
        int ty = y + PAD + (contentHeight < ICON && icon ? (ICON - contentHeight) / 2 : 0);
        g.text(font, name, x + textX, ty, NAME, true);
        ty += LINE;
        if (v.source() != null) {
            g.text(font, v.source().copy().withStyle(ChatFormatting.ITALIC), x + textX, ty, SOURCE, true);
            ty += LINE;
        }
        if (health != null) {
            if (health.hearts()) {
                hearts(g, x + textX, ty, health);
                ty += HEART + 1;
            } else {
                g.text(font, healthText(health), x + textX, ty, DETAIL, true);
                ty += LINE;
            }
        }
        for (Component line : v.lines()) {
            g.text(font, line, x + textX, ty, DETAIL, true);
            ty += LINE;
        }
        if (right > 0) {
            int rx = x + width - PAD - right;
            int ry = y + PAD;
            for (ItemStack tool : v.tools()) {
                g.item(tool, rx, ry);
                rx += ICON + 1;
            }
            if (v.mark() != ToolAdvice.Mark.NONE) {
                boolean check = v.mark() == ToolAdvice.Mark.CHECK;
                g.text(font, check ? "✔" : "✘", rx + 1, ry + 4, check ? CHECK : CROSS, true);
            }
        }
        if (progress >= 0.0f) {
            int barWidth = Math.round((width - 4) * progress);
            g.fill(x + 2, y + height - 3, x + 2 + barWidth, y + height - 2, PROGRESS);
        }
        lastDrawn = v;
        lastBounds = new int[] {x, y, width, height};
        lastProgress = progress;
        frames++;
    }

    private static Component healthText(EntityDetails.Health health) {
        return Component.translatable(TargetInfoTexts.HEALTH, health.current(), health.max());
    }

    private static void hearts(GuiGraphicsExtractor g, int x, int y, EntityDetails.Health health) {
        int containers = (health.maxHalves() + 1) / 2;
        for (int i = 0; i < containers; i++) {
            int hx = x + i * HEART_STEP;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, hx, y, HEART, HEART);
            int halves = health.halves() - i * 2;
            if (halves >= 2) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, hx, y, HEART, HEART);
            } else if (halves == 1) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, hx, y, HEART, HEART);
            }
        }
    }

    /**
     * The panel drawn in the last frame, or {@code null}; for development game tests. Vanilla skips HUD elements
     * entirely while the HUD is hidden, so a result older than a quarter second counts as nothing drawn.
     */
    public static TargetView lastDrawn() {
        return System.nanoTime() - lastExtractNanos > 250_000_000L ? null : lastDrawn;
    }

    /** x, y, width and height of the last drawn panel; for development game tests. */
    public static int[] lastBounds() {
        return lastBounds.clone();
    }

    /** The progress drawn in the last frame, 0–1, or negative when no bar was drawn. */
    public static float lastProgress() {
        return lastProgress;
    }

    public static long frames() {
        return frames;
    }
}
