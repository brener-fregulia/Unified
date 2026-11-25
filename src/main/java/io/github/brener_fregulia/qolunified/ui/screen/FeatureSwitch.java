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
    public static final int WIDTH = 52;
    public static final int HEIGHT = 20;
    private static final int KNOB = 12;
    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFA33A3A;
    private static final int GRAY = 0xFF5A5A5A;
    private static final int KNOB_FACE = 0xFFE6E6E6;
    private static final int KNOB_SHADOW = 0xFF8A8A8A;
    private static final int FRAME = 0xFF000000;

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
        int y = getY() + 2;
        int w = getWidth();
        int h = getHeight() - 4;
        int track = !active ? GRAY : on ? GREEN : RED;
        g.fill(x, y, x + w, y + h, FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, track);
        // Knob: right when ON, left when OFF.
        int kx = on ? x + w - 1 - KNOB - 1 : x + 2;
        g.fill(kx, y + 2, kx + KNOB, y + h - 2, KNOB_SHADOW);
        g.fill(kx, y + 2, kx + KNOB - 1, y + h - 3, KNOB_FACE);
        // Symbol and text on the free side.
        String symbol = on ? "✔" : "✕";
        String text = Component.translatable(on ? UiKeys.SWITCH_ON : UiKeys.SWITCH_OFF).getString();
        String content = on ? text + " " + symbol : symbol + " " + text;
        int freeLeft = on ? x + 2 : kx + KNOB;
        int freeRight = on ? kx : x + w - 2;
        int tx = freeLeft + (freeRight - freeLeft - font.width(content)) / 2;
        g.text(font, content, tx, y + (h - font.lineHeight) / 2 + 1, active ? 0xFFFFFFFF : 0xFFB0B0B0, true);
        if (isHoveredOrFocused() && active) {
            g.outline(x - 1, getY() + 1, w + 2, h + 2, 0xFFFFFFFF);
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
