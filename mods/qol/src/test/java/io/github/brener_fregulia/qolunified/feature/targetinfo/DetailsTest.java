package io.github.brener_fregulia.qolunified.feature.targetinfo;

import static io.github.brener_fregulia.qolunified.feature.targetinfo.TargetInfoTexts.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.feature.targetinfo.EntityDetails.Owner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DetailsTest {
    @Test
    void honeyShowsLevelOutOfFive() {
        assertEquals(List.of(DetailLine.of(HONEY, 0, 5)), BlockDetails.honey(0, 5));
        assertEquals(List.of(DetailLine.of(HONEY, 5, 5)), BlockDetails.honey(5, 5));
    }

    @Test
    void redstoneStatesAreTheBlocksOwn() {
        assertEquals(List.of(DetailLine.of(POWER, 15)), BlockDetails.wire(15));
        assertEquals(List.of(DetailLine.of(LEVER, DetailLine.of(ON))), BlockDetails.lever(true));
        assertEquals(List.of(DetailLine.of(LEVER, DetailLine.of(OFF))), BlockDetails.lever(false));
        assertEquals(List.of(DetailLine.of(BUTTON, DetailLine.of(PRESSED))), BlockDetails.button(true));
        assertEquals(List.of(DetailLine.of(BUTTON, DetailLine.of(RELEASED))), BlockDetails.button(false));
        assertEquals(List.of(DetailLine.of(LAMP, DetailLine.of(ON))), BlockDetails.lamp(true));
        assertEquals(List.of(DetailLine.of(DELAY, 3), DetailLine.of(POWERED), DetailLine.of(LOCKED)),
                BlockDetails.repeater(3, true, true));
        assertEquals(List.of(DetailLine.of(DELAY, 1), DetailLine.of(UNPOWERED)), BlockDetails.repeater(1, false, false));
        assertEquals(List.of(DetailLine.of(MODE, DetailLine.of(MODE_SUBTRACT)), DetailLine.of(UNPOWERED)),
                BlockDetails.comparator(true, false));
    }

    @Test
    void cropGrowthIsAPercentageUntilMature() {
        assertEquals(List.of(DetailLine.of(GROWTH, 0)), BlockDetails.growth(0, 7));
        assertEquals(List.of(DetailLine.of(GROWTH, 57)), BlockDetails.growth(4, 7));
        assertEquals(List.of(DetailLine.of(MATURE)), BlockDetails.growth(7, 7));
        assertEquals(List.of(DetailLine.of(GROWTH, 50)), BlockDetails.growth(1, 2), "cocoa");
        assertEquals(List.of(), BlockDetails.growth(0, 0));
    }

    @Test
    void healthUsesHeartsUpToTenHearts() {
        var sheep = EntityDetails.health(8.0f, 8.0f);
        assertTrue(sheep.hearts());
        assertEquals(8, sheep.halves());
        assertEquals(8, sheep.maxHalves());
        var hurt = EntityDetails.health(19.5f, 20.0f);
        assertTrue(hurt.hearts());
        assertEquals(20, hurt.halves(), "rounded up like the vanilla HUD");
        var golem = EntityDetails.health(83.25f, 100.0f);
        assertFalse(golem.hearts());
        assertEquals("83.3", golem.current());
        assertEquals("100", golem.max());
        assertEquals("0", EntityDetails.health(-1.0f, 20.0f).current());
    }

    @Test
    void ownerIsResolvedLocallyOrReportedUnknown() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertInstanceOf(Owner.None.class, EntityDetails.owner(Optional.empty(), u -> "x", u -> "y"), "untamed");
        assertEquals(new Owner.Named("Alex"), EntityDetails.owner(Optional.of(id), u -> "Alex", u -> "Steve"));
        assertEquals(new Owner.Named("Steve"), EntityDetails.owner(Optional.of(id), u -> null, u -> "Steve"));
        Owner unknown = EntityDetails.owner(Optional.of(id), u -> null, u -> null);
        assertInstanceOf(Owner.Unknown.class, unknown, "a UUID is never shown as a name");
        assertFalse(unknown.toString().contains(id.toString()));
    }

    @Test
    void overlayIsSuppressedWhenNothingUsefulCanBeShown() {
        var t = OverlayRules.Target.BLOCK;
        assertTrue(OverlayRules.visible(true, t, true, true, false, false, false));
        assertFalse(OverlayRules.visible(false, t, true, true, false, false, false), "disabled");
        assertFalse(OverlayRules.visible(true, OverlayRules.Target.NONE, true, true, false, false, false), "air");
        assertFalse(OverlayRules.visible(true, t, true, true, true, false, false), "F1");
        assertFalse(OverlayRules.visible(true, t, true, true, false, true, false), "screen open");
        assertFalse(OverlayRules.visible(true, t, true, true, false, false, true), "debug screen");
        assertFalse(OverlayRules.visible(true, t, false, true, false, false, false), "blocks off");
        assertFalse(OverlayRules.visible(true, OverlayRules.Target.ENTITY, true, false, false, false, false));
    }

    @Test
    void overlaySitsBelowBossBars() {
        assertEquals(OverlayRules.TOP, OverlayRules.top(0, 240));
        assertEquals(12 + 5 + OverlayRules.TOP, OverlayRules.top(1, 240));
        assertEquals(12 + 19 + 5 + OverlayRules.TOP, OverlayRules.top(2, 240));
        // Vanilla stops drawing bars at a third of the screen (80 px here: bars at 12, 31, 50, 69).
        assertEquals(OverlayRules.top(4, 240), OverlayRules.top(9, 240));
    }

    @Test
    void progressOnlyWhileBreakingTheTargetedBlock() {
        assertEquals(0.4f, OverlayRules.progress(true, true, false, false, 0.4f));
        assertTrue(OverlayRules.progress(false, true, false, false, 0.4f) < 0, "stopped");
        assertTrue(OverlayRules.progress(true, false, false, false, 0.4f) < 0, "other block");
        assertTrue(OverlayRules.progress(true, true, true, false, 0.4f) < 0, "creative");
        assertTrue(OverlayRules.progress(true, true, false, true, 0.0f) < 0, "instant");
        assertEquals(1.0f, OverlayRules.progress(true, true, false, false, 1.3f));
    }
}
