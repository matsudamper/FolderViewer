package net.matsudamper.folderviewer.ui.textviewer

import org.junit.Assert.assertEquals
import org.junit.Test

internal class TextViewerScrollTest {
    @Test
    fun textViewerTwoAxisScrollConsumed_followsFinger() {
        val consumed = textViewerTwoAxisScrollConsumed(
            deltaX = 10f,
            deltaY = -20f,
            consumeScrollX = { delta ->
                assertEquals(-10f, delta)
                delta
            },
            consumeScrollY = { delta ->
                assertEquals(20f, delta)
                delta
            },
        )
        assertEquals(10f, consumed.x)
        assertEquals(-20f, consumed.y)
    }

    @Test
    fun textViewerTwoAxisScrollConsumed_reportsOnlyWhatScrollConsumed() {
        val consumed = textViewerTwoAxisScrollConsumed(
            deltaX = 30f,
            deltaY = 12f,
            consumeScrollX = { 0f },
            consumeScrollY = { delta -> delta / 2f },
        )
        assertEquals(0f, consumed.x, 0f)
        assertEquals(6f, consumed.y, 0f)
    }

    @Test
    fun textViewerMatchDocumentPosition_includesOriginAndScale() {
        assertEquals(280f, textViewerMatchDocumentPosition(local = 100f, origin = 40f, scale = 2f), 0f)
    }

    @Test
    fun textViewerMatchAxisScrollTarget_skipsVisibleRange() {
        val target = textViewerMatchAxisScrollTarget(
            start = 20f,
            end = 40f,
            scroll = 0f,
            viewport = 100f,
        )
        assertEquals(null, target)
    }

    @Test
    fun textViewerMatchAxisScrollTarget_scrollsHiddenRangeIntoView() {
        val target = textViewerMatchAxisScrollTarget(
            start = 280f,
            end = 300f,
            scroll = 0f,
            viewport = 200f,
        )
        assertEquals(230, target)
    }
}
