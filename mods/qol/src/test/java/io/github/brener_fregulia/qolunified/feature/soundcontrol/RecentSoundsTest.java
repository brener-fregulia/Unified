package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Recent history ordering, capacity and counts (sound-control-v1.md §5.3). */
class RecentSoundsTest {
    private static List<String> ids(RecentSounds<String, String> recent) {
        return recent.snapshot().stream().map(RecentSounds.Entry::id).toList();
    }

    @Test
    void mostRecentFirstWithCountsAndLastCategory() {
        var recent = new RecentSounds<String, String>(4);
        recent.record("a:one", "blocks", 10);
        recent.record("a:two", "hostile", 20);
        recent.record("a:one", "players", 30);
        assertEquals(List.of("a:one", "a:two"), ids(recent));
        var first = recent.snapshot().getFirst();
        assertEquals(2, first.count());
        assertEquals("players", first.category());
        assertEquals(30, first.lastHeardMillis());
    }

    @Test
    void capacityEvictsLeastRecentlyHeard() {
        var recent = new RecentSounds<String, String>(128);
        for (int i = 0; i < 200; i++) {
            recent.record("a:s" + i, "blocks", i);
        }
        assertEquals(128, recent.size());
        assertEquals("a:s199", ids(recent).getFirst());
        assertEquals("a:s72", ids(recent).getLast());
        recent.record("a:s72", "blocks", 500);
        recent.record("a:new", "blocks", 501);
        assertEquals(List.of("a:new", "a:s72"), ids(recent).subList(0, 2), "a heard id is kept, the oldest goes");
        assertEquals(128, recent.size());
    }

    @Test
    void mutedSoundsAreRecordedLikeAnyOther() {
        // The listener records every resolved sound; muting happens later in vanilla's play (§3.2).
        var recent = new RecentSounds<String, String>(8);
        recent.record("minecraft:entity.villager.ambient", "neutral", 1);
        assertEquals(List.of("minecraft:entity.villager.ambient"), ids(recent));
    }

    @Test
    void clearAndNullIds() {
        var recent = new RecentSounds<String, String>(2);
        recent.record(null, "x", 1);
        assertEquals(0, recent.size());
        recent.record("a:b", "x", 1);
        recent.clear();
        assertEquals(List.of(), recent.snapshot());
        assertThrows(IllegalArgumentException.class, () -> new RecentSounds<String, String>(0));
    }

    @Test
    void concurrentRecordingStaysBoundedAndCounted() throws InterruptedException {
        var recent = new RecentSounds<String, String>(16);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 10_000; i++) {
                    recent.record("a:s" + (i % 8), "blocks", i);
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        assertEquals(8, recent.size());
        assertEquals(40_000, recent.snapshot().stream().mapToInt(RecentSounds.Entry::count).sum());
    }
}
