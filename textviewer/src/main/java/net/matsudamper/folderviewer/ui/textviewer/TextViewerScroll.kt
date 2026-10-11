package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

private const val MatchScrollAnchor = 0.25f

/**
 * [deltaX] と [deltaY] は指の移動量。
 * スクロール値が増えると内容は逆方向へ動くので、消費量を返す前に符号を戻す。
 */
internal fun textViewerTwoAxisScrollConsumed(
    deltaX: Float,
    deltaY: Float,
    consumeScrollX: (Float) -> Float,
    consumeScrollY: (Float) -> Float,
): Offset {
    val consumedX = consumeScrollX(-deltaX)
    val consumedY = consumeScrollY(-deltaY)
    return Offset(x = -consumedX, y = -consumedY)
}

internal fun textViewerMatchDocumentPosition(
    local: Float,
    origin: Float,
    scale: Float,
): Float {
    return (origin + local) * scale
}

internal fun textViewerMatchAxisScrollTarget(
    start: Float,
    end: Float,
    scroll: Float,
    viewport: Float,
): Int? {
    val visibleEnd = scroll + viewport
    if (start >= scroll && end <= visibleEnd) return null
    return (start - viewport * MatchScrollAnchor).roundToInt().coerceAtLeast(0)
}
