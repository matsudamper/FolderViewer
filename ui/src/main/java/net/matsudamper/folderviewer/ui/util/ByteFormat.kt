package net.matsudamper.folderviewer.ui.util

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * バイト数を人間が読みやすい形式（B, KB, MB, GB, TB）にフォーマットする
 *
 * @param bytes バイト数
 * @return フォーマットされた文字列（例: "1.5 MB", "500 bytes"）
 */
public fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0 bytes"

    val isNegative = bytes < 0
    val absoluteBytes = abs(bytes)

    if (absoluteBytes < 1024) {
        return if (isNegative) "-$absoluteBytes bytes" else "$absoluteBytes bytes"
    }

    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = minOf(
        (log10(absoluteBytes.toDouble()) / log10(1024.0)).toInt().coerceAtLeast(1),
        units.size,
    )

    val size = absoluteBytes / 1024.0.pow(digitGroups.toDouble())
    val formatted = "%.1f %s".format(size, units[digitGroups - 1])

    return if (isNegative) "-$formatted" else formatted
}
