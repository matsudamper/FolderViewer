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
    fun textViewerZoomScrollTarget_keepsScrolledCentroid() {
        val target = textViewerZoomScrollTarget(
            scrollX = 100f,
            scrollY = 40f,
            centroidX = 180f,
            centroidY = 90f,
            factor = 2f,
        )
        assertEquals(280f, target.x)
        assertEquals(130f, target.y)
    }

    @Test
    fun textViewerZoomScrollTarget_keepsCentroidWhenZoomingOut() {
        val target = textViewerZoomScrollTarget(
            scrollX = 100f,
            scrollY = 80f,
            centroidX = 150f,
            centroidY = 120f,
            factor = 0.5f,
        )
        assertEquals(25f, target.x)
        assertEquals(20f, target.y)
    }
}
