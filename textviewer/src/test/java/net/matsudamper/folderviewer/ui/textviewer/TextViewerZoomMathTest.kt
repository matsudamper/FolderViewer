package net.matsudamper.folderviewer.ui.textviewer

import org.junit.Assert.assertEquals
import org.junit.Test

internal class TextViewerZoomMathTest {
    @Test
    fun textViewerZoomScrollTarget_keepsCentroidContentPoint() {
        val target = textViewerZoomScrollTarget(
            scrollX = 0f,
            scrollY = 0f,
            centroidX = 100f,
            centroidY = 40f,
            factor = 2f,
        )
        assertEquals(100f, target.x)
        assertEquals(40f, target.y)
    }

    @Test
    fun textViewerZoomScrollTarget_includesCurrentScroll() {
        val target = textViewerZoomScrollTarget(
            scrollX = 50f,
            scrollY = 20f,
            centroidX = 10f,
            centroidY = 5f,
            factor = 2f,
        )
        assertEquals(110f, target.x)
        assertEquals(45f, target.y)
    }
}
