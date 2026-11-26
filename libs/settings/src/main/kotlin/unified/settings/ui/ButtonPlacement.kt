package unified.settings.ui

/** An axis-aligned rectangle in GUI pixels. */
data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun intersects(other: Rect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

/**
 * Where the Options-screen button goes (Q-US4): the first free spot, in the screen's own footer or header bands, that
 * overlaps no existing widget and not the title, at the current size and GUI scale. Pure, so every scale is
 * unit-tested; null if nothing fits (then no button is added rather than covering something).
 */
object ButtonPlacement {
    private const val MARGIN = 5

    @JvmStatic
    fun choose(screenWidth: Int, screenHeight: Int, footer: Rect?, title: Rect?, occupied: List<Rect>,
               width: Int, height: Int): Rect? {
        val candidates = buildList {
            if (footer != null) {
                // Beside the footer's Done button: left first, then right.
                add(Rect(footer.x - MARGIN - width, footer.y, width, height))
                add(Rect(footer.right + MARGIN, footer.y, width, height))
            }
            val headerY = if (title != null) title.y + (title.height - height) / 2 else MARGIN
            add(Rect(MARGIN, headerY.coerceAtLeast(0), width, height))
            add(Rect(screenWidth - MARGIN - width, headerY.coerceAtLeast(0), width, height))
        }
        val blocked = occupied + listOfNotNull(title?.let { Rect(it.x - MARGIN, it.y, it.width + 2 * MARGIN, it.height) })
        return candidates.firstOrNull { c ->
            c.x >= 0 && c.y >= 0 && c.right <= screenWidth && c.bottom <= screenHeight &&
                blocked.none { it.intersects(c) }
        }
    }
}
