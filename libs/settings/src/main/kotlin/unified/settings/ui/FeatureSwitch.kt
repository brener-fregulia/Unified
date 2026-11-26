package unified.settings.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.Component

/**
 * The shared ON/OFF switch of a feature (library 1.2.0), drawn like the approved Unified: QoL workspace switch: a
 * green track with the knob on the right, a check and "ON", or a red track with the knob on the left, a cross and
 * "OFF"; gray when inactive. Knob side, symbol and text carry the state without relying on color. It only reports the
 * new value; the owning module validates, saves and applies it.
 */
class FeatureSwitch(private val featureName: Component, on: Boolean, private val onChange: (Boolean) -> Unit) :
    AbstractButton(0, 0, WIDTH, HEIGHT, label(on)) {

    var isOn: Boolean = on
        private set

    override fun onPress(input: InputWithModifiers) {
        isOn = !isOn
        message = label(isOn)
        onChange(isOn)
    }

    override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        val x = x
        val y = y
        val w = width
        val h = height
        val hot = active && isHovered
        val track = when {
            !active -> GRAY
            isOn -> if (hot) GREEN_HOVER else GREEN
            else -> if (hot) RED_HOVER else RED
        }
        g.fill(x, y + 1, x + w, y + h - 1, BORDER)
        g.fill(x + 1, y, x + w - 1, y + h, BORDER)
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, track)
        g.fill(x + 2, y + 1, x + w - 2, y + 2, HIGHLIGHT)
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, SHADE)
        val kx = if (isOn) x + w - 2 - KNOB_W else x + 2
        val ky = y + 2
        val kh = h - 4
        g.fill(kx, ky, kx + KNOB_W, ky + kh, BORDER)
        g.fill(kx + 1, ky + 1, kx + KNOB_W - 1, ky + kh - 1, if (active) KNOB_FACE else 0xFFB8B8B8.toInt())
        g.fill(kx + 1, ky + 1, kx + KNOB_W - 1, ky + 2, KNOB_LIGHT)
        g.fill(kx + 1, ky + 1, kx + 2, ky + kh - 1, KNOB_LIGHT)
        g.fill(kx + 1, ky + kh - 2, kx + KNOB_W - 1, ky + kh - 1, KNOB_DARK)
        g.fill(kx + KNOB_W - 2, ky + 1, kx + KNOB_W - 1, ky + kh - 1, KNOB_DARK)
        val text = Component.translatable(if (isOn) WorkspaceKeys.SWITCH_ON else WorkspaceKeys.SWITCH_OFF).string
        val content = if (isOn) "$text ✔" else "✕ $text"
        val freeLeft = if (isOn) x + 3 else kx + KNOB_W
        val freeRight = if (isOn) kx else x + w - 3
        val tx = freeLeft + (freeRight - freeLeft - font.width(content)) / 2
        g.text(font, content, tx, y + (h - font.lineHeight) / 2 + 1, if (active) -0x1 else 0xFFB0B0B0.toInt(), true)
        if (isFocused && active) {
            g.outline(x - 1, y - 1, w + 2, h + 2, -0x1)
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, Component.translatable(WorkspaceKeys.SWITCH_NARRATION, featureName,
            Component.translatable(if (isOn) WorkspaceKeys.SWITCH_ON else WorkspaceKeys.SWITCH_OFF)))
        if (active) {
            output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"))
        }
    }

    companion object {
        const val WIDTH: Int = 54
        const val HEIGHT: Int = 20
        private const val KNOB_W = 14
        private val BORDER = 0xFF161616.toInt()
        private val GREEN = 0xFF3E7B3A.toInt()
        private val GREEN_HOVER = 0xFF4A8F45.toInt()
        private val RED = 0xFF8C3535.toInt()
        private val RED_HOVER = 0xFFA03E3E.toInt()
        private val GRAY = 0xFF4E4E4E.toInt()
        private const val HIGHLIGHT = 0x30FFFFFF
        private const val SHADE = 0x40000000
        private val KNOB_FACE = 0xFFEDEDED.toInt()
        private const val KNOB_LIGHT = -0x1
        private val KNOB_DARK = 0xFF9A9A9A.toInt()

        private fun label(on: Boolean): Component =
            Component.translatable(if (on) WorkspaceKeys.SWITCH_ON else WorkspaceKeys.SWITCH_OFF)
    }
}
