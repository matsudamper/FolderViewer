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
}
