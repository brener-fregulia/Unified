package unified.qol.feature.shulkerpreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class ShulkerHintTest {
    @Test
    void onlyTheKeyIsGold() {
        Component hint = ShulkerHint.text();
        TranslatableContents outer = (TranslatableContents) hint.getContents();
        assertEquals(ShulkerPreviewTexts.SHIFT_HINT, outer.getKey());
        assertNull(hint.getStyle().getColor(), "the rest keeps the tooltip's muted color");
        Component key = (Component) outer.getArgs()[0];
        assertEquals(ShulkerPreviewTexts.SHIFT_KEY, ((TranslatableContents) key.getContents()).getKey());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), key.getStyle().getColor());
    }
}
