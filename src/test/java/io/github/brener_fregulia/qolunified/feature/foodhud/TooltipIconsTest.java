package io.github.brener_fregulia.qolunified.feature.foodhud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TooltipIconsTest {
    private static final float EPSILON = 1.0e-4f;

    @Test
    void hungerPartialIconComesFirst() {
        assertArrayEquals(new float[] {0.5f}, TooltipIcons.fills(1), EPSILON);
        assertArrayEquals(new float[] {0.5f, 1.0f}, TooltipIcons.fills(3), EPSILON);
        assertArrayEquals(new float[] {0.5f, 1.0f, 1.0f}, TooltipIcons.fills(5), EPSILON);
        assertArrayEquals(new float[] {1.0f, 1.0f, 1.0f, 1.0f}, TooltipIcons.fills(8), EPSILON);
    }

    @Test
    void saturationIsProportionalWithThePartialIconFirst() {
        assertArrayEquals(new float[] {0.15f}, TooltipIcons.fills(0.3f), EPSILON);
        assertArrayEquals(new float[] {0.6f}, TooltipIcons.fills(1.2f), EPSILON);
        assertArrayEquals(new float[] {1.0f, 1.0f, 1.0f}, TooltipIcons.fills(6.0f), EPSILON);
        float[] goldenCarrot = TooltipIcons.fills(14.4f);
        assertEquals(8, goldenCarrot.length);
        assertEquals(0.2f, goldenCarrot[0], EPSILON);
        for (int i = 1; i < goldenCarrot.length; i++) {
            assertEquals(1.0f, goldenCarrot[i], EPSILON);
        }
    }

    @Test
    void partialIconsFillFromTheLeftAndStayVisible() {
        assertEquals(5, TooltipIcons.filledColumns(0.5f), "half shank: left 5 of 9 columns");
        assertEquals(1, TooltipIcons.filledColumns(0.15f), "saturation 0.3 still shows a sliver");
        assertEquals(5, TooltipIcons.filledColumns(0.6f));
        assertEquals(9, TooltipIcons.filledColumns(1.0f));
        assertEquals(0, TooltipIcons.filledColumns(0.0f));
    }

    @Test
    void emptyAndOverflowingValues() {
        assertEquals(0, TooltipIcons.fills(0.0f).length);
        assertEquals(0, TooltipIcons.fills(-2.0f).length);
        assertEquals(0, TooltipIcons.fills(Float.NaN).length);
        assertArrayEquals(new float[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, TooltipIcons.fills(25.0f), EPSILON);
    }
}
