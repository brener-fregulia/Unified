package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MouseLayoutTest {
    @Test
    void buttonNumbersAreMinecraftsOwn() {
        assertEquals(InputConstants.MOUSE_BUTTON_LEFT, MouseLayout.LEFT);
        assertEquals(InputConstants.MOUSE_BUTTON_MIDDLE, MouseLayout.MIDDLE);
        assertEquals(InputConstants.MOUSE_BUTTON_RIGHT, MouseLayout.RIGHT);
        assertEquals(InputConstants.MOUSE_BUTTON_4, MouseLayout.BACK);
        assertEquals(InputConstants.MOUSE_BUTTON_5, MouseLayout.FORWARD);
        var buttons = new HashSet<Integer>();
        MouseLayout.REGIONS.forEach(r -> assertTrue(buttons.add(r.button())));
        assertEquals(Set.of(1, 2, 3, 4, 5), buttons, "exactly five regions; no wheel up or down");
    }

    @Test
    void regionsDoNotOverlapAndStayInsideTheDrawing() {
        List<MouseLayout.Region> regions = MouseLayout.REGIONS;
        for (int i = 0; i < regions.size(); i++) {
            MouseLayout.Region r = regions.get(i);
            assertTrue(r.x() >= 0 && r.y() >= 0 && r.x() + r.width() <= MouseLayout.WIDTH
                    && r.y() + r.height() <= MouseLayout.HEIGHT, "inside: " + r);
            for (int j = i + 1; j < regions.size(); j++) {
                assertFalse(r.overlaps(regions.get(j)), r + " overlaps " + regions.get(j));
            }
        }
    }

    @Test
    void shapeReadsAsAMouse() {
        var left = MouseLayout.region(MouseLayout.LEFT).orElseThrow();
        var right = MouseLayout.region(MouseLayout.RIGHT).orElseThrow();
        var wheel = MouseLayout.region(MouseLayout.MIDDLE).orElseThrow();
        assertEquals(left.width(), right.width(), "symmetric buttons");
        assertTrue(left.x() < wheel.x() && wheel.x() + wheel.width() <= right.x(), "wheel between the buttons");
        for (int side : new int[] {MouseLayout.BACK, MouseLayout.FORWARD}) {
            var s = MouseLayout.region(side).orElseThrow();
            assertTrue(s.x() < MouseLayout.BODY_X, "side button on the left flank");
            assertTrue(s.y() > left.y() + left.height(), "side buttons below the main buttons");
        }
        assertTrue(MouseLayout.region(MouseLayout.FORWARD).orElseThrow().y()
                < MouseLayout.region(MouseLayout.BACK).orElseThrow().y(), "5 above 4");
    }

    @Test
    void hitTestingFindsEachRegionAndNothingOnTheShell() {
        for (MouseLayout.Region r : MouseLayout.REGIONS) {
            assertEquals(r.button(), MouseLayout.regionAt(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0)
                    .orElseThrow().button());
        }
        assertTrue(MouseLayout.regionAt(7, 0.5).isEmpty(), "body above the wheel is not a button");
        assertTrue(MouseLayout.regionAt(7, 13).isEmpty(), "palm area is not a button");
        assertTrue(MouseLayout.onBody(7, 13));
        assertTrue(MouseLayout.regionAt(-1, 0).isEmpty());
        assertTrue(MouseLayout.regionAt(MouseLayout.WIDTH, 0).isEmpty());
    }
}
