package net.matsudamper.folderviewer.ui.textviewer

internal data class TextViewerZoomScroll(
    val x: Float,
    val y: Float,
)

internal fun textViewerZoomScrollTarget(
    scrollX: Float,
    scrollY: Float,
    centroidX: Float,
    centroidY: Float,
    factor: Float,
): TextViewerZoomScroll {
    val contentX = scrollX + centroidX
    val contentY = scrollY + centroidY
    return TextViewerZoomScroll(
        x = contentX * factor - centroidX,
        y = contentY * factor - centroidY,
    )
}
