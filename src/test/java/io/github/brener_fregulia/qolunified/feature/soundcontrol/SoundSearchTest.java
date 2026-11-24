package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Search and filter semantics (sound-control-v1.md §5.4). */
class SoundSearchTest {
    private static final SoundSearch.Candidate VILLAGER = new SoundSearch.Candidate(
            "minecraft:entity.villager.ambient", "Aldeão resmunga", "minecraft", "Minecraft", "neutral",
            "Criaturas amigáveis");
    private static final SoundSearch.Candidate HUM = new SoundSearch.Candidate(
            "examplemod:machine.hum", "machine.hum", "examplemod", "Example Mod", null, null);

    @Test
    void matchesNameIdNamespaceModAndCategory() {
        assertTrue(SoundSearch.matches(VILLAGER, "aldeão", null, null), "localized name");
        assertTrue(SoundSearch.matches(VILLAGER, "ALDEÃO RESMUNGA", null, null), "case-insensitive");
        assertTrue(SoundSearch.matches(VILLAGER, "entity.villager", null, null), "id");
        assertTrue(SoundSearch.matches(VILLAGER, "minecraft:", null, null), "namespace");
        assertTrue(SoundSearch.matches(HUM, "example mod", null, null), "mod display name");
        assertTrue(SoundSearch.matches(VILLAGER, "neutral", null, null), "raw category");
        assertTrue(SoundSearch.matches(VILLAGER, "amigáveis", null, null), "localized category");
        assertTrue(SoundSearch.matches(VILLAGER, "  villager   ambient ", null, null), "all words, any order");
        assertFalse(SoundSearch.matches(VILLAGER, "villager hurt", null, null));
        assertTrue(SoundSearch.matches(VILLAGER, "", null, null));
    }

    @Test
    void filtersUseKnownInformationOnly() {
        assertTrue(SoundSearch.matches(VILLAGER, "", "neutral", "minecraft"));
        assertFalse(SoundSearch.matches(VILLAGER, "", "hostile", null));
        assertFalse(SoundSearch.matches(HUM, "", "blocks", null), "never heard: category unknown");
        assertTrue(SoundSearch.matches(HUM, "", null, "examplemod"));
        assertFalse(SoundSearch.matches(VILLAGER, "", null, "examplemod"));
    }

    @Test
    void allSoundsTabNeedsAQueryOrAFilter() {
        assertFalse(SoundSearch.listsAll("", null, null));
        assertFalse(SoundSearch.listsAll(" a ", null, null));
        assertTrue(SoundSearch.listsAll("ab", null, null));
        assertTrue(SoundSearch.listsAll("", "hostile", null));
        assertTrue(SoundSearch.listsAll("", null, "minecraft"));
    }

    @Test
    void sortedByNameThenId() {
        var list = new ArrayList<>(List.of(VILLAGER, HUM));
        list.sort(SoundSearch.BY_NAME);
        assertEquals(List.of(VILLAGER, HUM), list);
    }
}
