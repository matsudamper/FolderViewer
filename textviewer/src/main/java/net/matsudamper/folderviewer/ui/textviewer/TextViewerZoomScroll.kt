package net.matsudamper.folderviewer.ui.textviewer

internal data class TextViewerZoomScroll(
    val x: Float,
    val y: Float,
)

/**
 * ピンチの重心が画面上で動かないスクロール位置を返す。
 *
 * [centroidX] と [centroidY] はスクロール内のコンテンツ座標で、現在のスクロール量を既に含む。
 */
internal fun textViewerZoomScrollTarget(
    scrollX: Float,
    scrollY: Float,
    centroidX: Float,
    centroidY: Float,
    factor: Float,
): TextViewerZoomScroll {
    val scaleDelta = factor - 1f
    return TextViewerZoomScroll(
        x = scrollX + centroidX * scaleDelta,
        y = scrollY + centroidY * scaleDelta,
    )
}
