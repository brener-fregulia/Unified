package unified.qol.feature.foodhud;

import static unified.qol.feature.foodhud.HungerPreview.Icon.FULL;
import static unified.qol.feature.foodhud.HungerPreview.Icon.HALF;
import static unified.qol.feature.foodhud.HungerPreview.Icon.NONE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import unified.qol.feature.foodhud.HungerPreview.Icon;
import org.junit.jupiter.api.Test;

/** Slot 0 is vanilla's first (rightmost) hunger icon; slot n holds points 2n+1 and 2n+2. */
class HungerPreviewTest {
    private static Icon[] preview(int food, int restored) {
        return HungerPreview.icons(true, true, food,
                FoodMath.afterEating(food, 0.0f, restored, 0.0f).food());
    }

    private static Icon[] expect(int firstSlot, Icon... icons) {
        Icon[] all = new Icon[HungerPreview.SLOTS];
        java.util.Arrays.fill(all, NONE);
        System.arraycopy(icons, 0, all, firstSlot, icons.length);
        return all;
    }

    @Test
    void evenFoodPlusOneAddsAHalfInTheNextSlot() {
        assertArrayEquals(expect(5, HALF), preview(10, 1));
    }

    @Test
    void oddFoodPlusOneCompletesTheExistingHalf() {
        assertArrayEquals(expect(5, FULL), preview(11, 1));
    }

    @Test
    void evenFoodPlusTwoAddsOneFullShank() {
        assertArrayEquals(expect(5, FULL), preview(10, 2));
    }

    @Test
    void oddFoodPlusTwoCompletesThenAddsAHalf() {
        assertArrayEquals(expect(5, FULL, HALF), preview(11, 2));
    }

    @Test
    void restoringThreePoints() {
        assertArrayEquals(expect(5, FULL, HALF), preview(10, 3));
        assertArrayEquals(expect(5, FULL, FULL), preview(11, 3));
    }

    @Test
    void clampsAtTwenty() {
        assertArrayEquals(expect(9, FULL), preview(19, 5), "19 + 5 only completes the last half");
        assertArrayEquals(expect(0), preview(20, 4), "full: nothing to preview");
        assertArrayEquals(expect(8, FULL, FULL), HungerPreview.icons(true, true, 16, 99));
    }

    @Test
    void disabledPreviewOrFeatureDrawsNothing() {
        assertArrayEquals(expect(0), HungerPreview.icons(false, true, 10, 15));
        assertArrayEquals(expect(0), HungerPreview.icons(true, false, 10, 15));
        assertArrayEquals(expect(0), HungerPreview.icons(true, true, 10, null));
    }

    @Test
    void vanillaSpritesIncludingHungerEffectVariants() {
        assertEquals("hud/food_full", HungerPreview.spritePath(FULL, false));
        assertEquals("hud/food_half", HungerPreview.spritePath(HALF, false));
        assertEquals("hud/food_full_hunger", HungerPreview.spritePath(FULL, true));
        assertEquals("hud/food_half_hunger", HungerPreview.spritePath(HALF, true));
        assertNull(HungerPreview.spritePath(NONE, true));
    }
}
