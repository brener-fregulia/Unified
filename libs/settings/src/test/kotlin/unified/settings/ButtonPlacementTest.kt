package unified.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import unified.settings.ui.ButtonPlacement
import unified.settings.ui.Rect

class ButtonPlacementTest {
    /** Vanilla Options: a 200 px Done button centered in a 33 px footer; the grid in between. */
    private fun options(width: Int, height: Int): Triple<Rect, Rect, List<Rect>> {
        val done = Rect((width - 200) / 2, height - 27, 200, 20)
        val title = Rect((width - 40) / 2, 12, 40, 9)
        val grid = Rect((width - 310) / 2, 40, 310, height - 80)
        return Triple(done, title, listOf(done, title, grid))
    }

    @Test
    fun besideTheDoneButtonWhenThereIsRoom() {
        for ((w, h) in listOf(854 / 2 to 480 / 2, 1280 / 2 to 720 / 2, 1920 / 4 to 1080 / 4, 2560 / 2 to 1080 / 2)) {
            val (done, title, occupied) = options(w, h)
            val spot = requireNotNull(ButtonPlacement.choose(w, h, done, title, occupied, 100, 20))
            assertEquals(done.y, spot.y, "footer row at ${w}x$h")
            assertTrue(occupied.none { it.intersects(spot) }, "no overlap at ${w}x$h")
            assertTrue(spot.x >= 0 && spot.right <= w)
        }
    }

    @Test
    fun theHeaderWhenTheFooterIsTooNarrow() {
        val (done, title, occupied) = options(320, 240)
        val spot = requireNotNull(ButtonPlacement.choose(320, 240, done, title, occupied, 100, 20))
        assertTrue(spot.y < 33, "header band")
        assertTrue(occupied.none { it.intersects(spot) })
    }

    @Test
    fun nothingRatherThanAnOverlap() {
        val full = listOf(Rect(0, 0, 200, 200))
        assertNull(ButtonPlacement.choose(200, 200, null, null, full, 100, 20))
    }
}
