package io.github.brener_fregulia.qolunified.feature.foodhud;

import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Hand.MAIN;
import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Hand.NONE;
import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Hand.OFF;
import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Use.EDIBLE_FOOD;
import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Use.EMPTY;
import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Use.INEDIBLE_FOOD;
import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Use.NO_USE;
import static io.github.brener_fregulia.qolunified.feature.foodhud.HandSelection.Use.OTHER_USE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HandSelectionTest {
    @Test
    void edibleMainHandFoodWins() {
        assertEquals(MAIN, HandSelection.select(EDIBLE_FOOD, EDIBLE_FOOD, true));
        assertEquals(MAIN, HandSelection.select(EDIBLE_FOOD, EMPTY, false));
    }

    @Test
    void offHandIsReachedWhenTheMainHandUsePasses() {
        assertEquals(OFF, HandSelection.select(EMPTY, EDIBLE_FOOD, true));
        assertEquals(OFF, HandSelection.select(NO_USE, EDIBLE_FOOD, true));
        // Main-hand food that cannot be eaten now (full) fails, so the off-hand golden apple is eaten.
        assertEquals(OFF, HandSelection.select(INEDIBLE_FOOD, EDIBLE_FOOD, true));
    }

    @Test
    void otherMainHandUsesStopTheOffHand() {
        assertEquals(NONE, HandSelection.select(OTHER_USE, EDIBLE_FOOD, true));
    }

    @Test
    void offHandPreviewSettingIsRespected() {
        assertEquals(NONE, HandSelection.select(EMPTY, EDIBLE_FOOD, false));
        assertEquals(NONE, HandSelection.select(INEDIBLE_FOOD, INEDIBLE_FOOD, true));
        assertEquals(NONE, HandSelection.select(EMPTY, EMPTY, true));
    }
}
