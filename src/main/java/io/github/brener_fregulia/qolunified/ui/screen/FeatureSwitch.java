package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractButton;

/**
 * The master ON/OFF switch of a feature (settings-ux-v1.md §6, Q-SU2): a green track with the knob on the right, a
 * check and "ON", or a red track with the knob on the left, a cross and "OFF"; gray when locked. Knob side, symbol and
 * text carry the state without relying on color. It only reports the new value; the caller writes it to the draft.
 */
public final class FeatureSwitch extends AbstractButton {
    public static final int WIDTH = 54;
    public static final int HEIGHT = 20;
    private static final int KNOB_W = 14;
    // Muted pixel-art palette: border, track, a one-pixel top highlight and bottom shade on the track.
    private static final int BORDER = 0xFF161616;
    private static final int GREEN = 0xFF3E7B3A;
    private static final int GREEN_HOVER = 0xFF4A8F45;
    private static final int RED = 0xFF8C3535;
    private static final int RED_HOVER = 0xFFA03E3E;
    private static final int GRAY = 0xFF4E4E4E;
    private static final int HIGHLIGHT = 0x30FFFFFF;
    private static final int SHADE = 0x40000000;
    private static final int KNOB_FACE = 0xFFEDEDED;
    private static final int KNOB_LIGHT = 0xFFFFFFFF;
    private static final int KNOB_DARK = 0xFF9A9A9A;
    private static final int FOCUS = 0xFFFFFFFF;

    private final Component featureName;
    private final Consumer<Boolean> onChange;
    private boolean on;

    public FeatureSwitch(Component featureName, boolean on, Consumer<Boolean> onChange) {
        super(0, 0, WIDTH, HEIGHT, label(on));
        this.featureName = featureName;
        this.on = on;
        this.onChange = onChange;
    }

    /** The widget's message is the state key, so tests and narration find "ON"/"OFF". */
    private static Component label(boolean on) {
        return Component.translatable(on ? UiKeys.SWITCH_ON : UiKeys.SWITCH_OFF);
    }

    public boolean isOn() {
        return on;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        on = !on;
        setMessage(label(on));
        onChange.accept(on);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = active && isHovered();
        int track = !active ? GRAY : on ? (hot ? GREEN_HOVER : GREEN) : (hot ? RED_HOVER : RED);
        // Border, track, then a one-pixel highlight on top and a shade at the bottom of the track.
        g.fill(x, y + 1, x + w, y + h - 1, BORDER);
        g.fill(x + 1, y, x + w - 1, y + h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, track);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, HIGHLIGHT);
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, SHADE);
        // Knob: right when ON, left when OFF, with a light top-left edge and a dark bottom-right edge.
        int kx = on ? x + w - 2 - KNOB_W : x + 2;
        int ky = y + 2;
        int kh = h - 4;
        g.fill(kx, ky, kx + KNOB_W, ky + kh, BORDER);
        g.fill(kx + 1, ky + 1, kx + KNOB_W - 1, ky + kh - 1, active ? KNOB_FACE : 0xFFB8B8B8);
        g.fill(kx + 1, ky + 1, kx + KNOB_W - 1, ky + 2, KNOB_LIGHT);
        g.fill(kx + 1, ky + 1, kx + 2, ky + kh - 1, KNOB_LIGHT);
        g.fill(kx + 1, ky + kh - 2, kx + KNOB_W - 1, ky + kh - 1, KNOB_DARK);
        g.fill(kx + KNOB_W - 2, ky + 1, kx + KNOB_W - 1, ky + kh - 1, KNOB_DARK);
        // Label and symbol on the free side: "ON ✔" on the left, "✕ OFF" on the right.
        String text = Component.translatable(on ? UiKeys.SWITCH_ON : UiKeys.SWITCH_OFF).getString();
        String content = on ? text + " ✔" : "✕ " + text;
        int freeLeft = on ? x + 3 : kx + KNOB_W;
        int freeRight = on ? kx : x + w - 3;
        int tx = freeLeft + (freeRight - freeLeft - font.width(content)) / 2;
        g.text(font, content, tx, y + (h - font.lineHeight) / 2 + 1, active ? 0xFFFFFFFF : 0xFFB0B0B0, true);
        if (isFocused() && active) {
            g.outline(x - 1, y - 1, w + 2, h + 2, FOCUS);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable(UiKeys.SWITCH_NARRATION, featureName,
                Component.translatable(on ? UiKeys.SWITCH_ON : UiKeys.SWITCH_OFF)));
        if (active) {
            output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"));
        }
    }
}
