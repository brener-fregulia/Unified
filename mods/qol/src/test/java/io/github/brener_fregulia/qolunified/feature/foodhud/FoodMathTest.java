package io.github.brener_fregulia.qolunified.feature.foodhud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.feature.foodhud.FoodMath.Prediction;
import org.junit.jupiter.api.Test;

class FoodMathTest {
    private static final float EPSILON = 1.0e-4f;

    @Test
    void breadAndSteakMatchFhAc2() {
        assertEquals(new Prediction(15, 6.0f), FoodMath.afterEating(10, 0.0f, 5, 6.0f));
        Prediction steak = FoodMath.afterEating(16, 0.0f, 8, 12.8f);
        assertEquals(20, steak.food());
        assertEquals(12.8f, steak.saturation(), EPSILON);
    }

    @Test
    void foodAndSaturationAreClamped() {
        assertEquals(new Prediction(20, 20.0f), FoodMath.afterEating(19, 19.0f, 8, 12.8f));
        // Saturation never exceeds the new food level.
        assertEquals(new Prediction(3, 3.0f), FoodMath.afterEating(1, 0.0f, 2, 9.0f));
        assertEquals(new Prediction(0, 0.0f), FoodMath.afterEating(0, 0.0f, -4, -1.0f));
    }

    @Test
    void canEatFollowsPlayerCanEat() {
        assertTrue(FoodMath.canEat(false, false, 19));
        assertFalse(FoodMath.canEat(false, false, 20));
        assertTrue(FoodMath.canEat(false, true, 20), "golden apple at full food");
        assertTrue(FoodMath.canEat(true, false, 20), "invulnerable (Creative)");
    }

    @Test
    void fullFoodWithSaturationHealsFromSaturation() {
        // Food 20, saturation 6: 6 saturation of fast healing heals 4 HP (2/3 HP per saturation point),
        // then slow healing continues while food stays at 18 or more.
        float gain = FoodMath.estimateHealthGain(20, 6.0f, 1.0f, 20.0f, false, true);
        assertTrue(gain > 4.0f, "gain " + gain);
        // 20 saturation heals about 2/3 HP per point, then slow healing runs until food falls below 18.
        assertEquals(15.5f, FoodMath.estimateHealthGain(20, 20.0f, 1.0f, 20.0f, false, true), EPSILON);
    }

    @Test
    void slowHealingAtFood18And19() {
        // Saturation 0, food 18: one heal (6 exhaustion), then food drops to 17 and healing stops.
        assertEquals(1.0f, FoodMath.estimateHealthGain(18, 0.0f, 10.0f, 20.0f, false, true), EPSILON);
        assertEquals(2.0f, FoodMath.estimateHealthGain(19, 0.0f, 10.0f, 20.0f, false, true), EPSILON);
        assertEquals(0.0f, FoodMath.estimateHealthGain(17, 0.0f, 10.0f, 20.0f, false, true), EPSILON);
    }

    @Test
    void gainIsCappedAtMissingHealth() {
        assertEquals(0.5f, FoodMath.estimateHealthGain(20, 20.0f, 19.5f, 20.0f, false, true), EPSILON);
        assertEquals(0.0f, FoodMath.estimateHealthGain(20, 20.0f, 20.0f, 20.0f, false, true), EPSILON);
    }

    @Test
    void peacefulAndDisabledRegenerationGiveNoEstimate() {
        assertEquals(0.0f, FoodMath.estimateHealthGain(20, 20.0f, 1.0f, 20.0f, true, true), EPSILON);
        assertEquals(0.0f, FoodMath.estimateHealthGain(20, 20.0f, 1.0f, 20.0f, false, false), EPSILON);
    }

    @Test
    void invalidValuesGiveNoEstimate() {
        assertEquals(0.0f, FoodMath.estimateHealthGain(20, Float.NaN, 1.0f, 20.0f, false, true), EPSILON);
        assertEquals(0.0f, FoodMath.estimateHealthGain(20, 5.0f, Float.NaN, 20.0f, false, true), EPSILON);
        assertEquals(0.0f, FoodMath.estimateHealthGain(-5, 0.0f, 1.0f, 20.0f, false, true), EPSILON);
    }

    @Test
    void iconSteps() {
        // FH-AC-1: saturation 5 covers 2.5 icons.
        assertEquals(4, FoodMath.saturationQuarters(5.0f, 0));
        assertEquals(4, FoodMath.saturationQuarters(5.0f, 1));
        assertEquals(2, FoodMath.saturationQuarters(5.0f, 2));
        assertEquals(0, FoodMath.saturationQuarters(5.0f, 3));
        assertEquals(1, FoodMath.saturationQuarters(0.1f, 0));
        assertEquals(0, FoodMath.saturationQuarters(0.0f, 0));
        assertEquals(1, FoodMath.iconHalves(15.0f, 7));
        assertEquals(2, FoodMath.iconHalves(15.0f, 6));
        assertEquals(0, FoodMath.iconHalves(15.0f, 8));
    }
}
