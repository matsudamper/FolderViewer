package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.ui.geometry.Offset

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
