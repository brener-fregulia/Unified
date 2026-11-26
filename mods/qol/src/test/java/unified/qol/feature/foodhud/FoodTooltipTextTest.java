package unified.qol.feature.foodhud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoodTooltipTextTest {
    @Test
    void hungerIsASignedWholeNumber() {
        assertEquals("+1", FoodTooltipText.hunger(1));
        assertEquals("+5", FoodTooltipText.hunger(5));
        assertEquals("+0", FoodTooltipText.hunger(0));
    }

    @Test
    void saturationUsesTheLanguageDecimalSeparator() {
        // Bread 6.0, beetroot 1.2, steak 12.8 (float values as stored in FoodProperties).
        assertEquals("+6.0", FoodTooltipText.saturation(6.0f, "en_us"));
        assertEquals("+1.2", FoodTooltipText.saturation(1.2000000476837158f, "en_us"));
        assertEquals("+12.8", FoodTooltipText.saturation(12.8f, "en_us"));
        assertEquals("+1,2", FoodTooltipText.saturation(1.2f, "pt_br"));
        assertEquals("+12,8", FoodTooltipText.saturation(12.8f, "pt_br"));
    }

    @Test
    void lowAndUnusualValuesStayReadable() {
        assertEquals("+0.6", FoodTooltipText.saturation(0.6f, "en_us"));
        assertEquals("+0.1", FoodTooltipText.saturation(0.05f, "en_us"));
        assertEquals("+14.4", FoodTooltipText.saturation(14.4f, "en_us"));
        assertEquals("-0.5", FoodTooltipText.saturation(-0.5f, "en_us"));
        assertEquals("+2.0", FoodTooltipText.saturation(2.0f, null));
        assertEquals("+2.0", FoodTooltipText.saturation(2.0f, "xx_yy"));
    }
}
