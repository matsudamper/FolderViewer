package net.matsudamper.folderviewer.ui.util

import kotlin.math.log10
import kotlin.math.pow

/**
 * バイト数を人間が読みやすい形式（B, KB, MB, GB, TB）にフォーマットする
 *
 * @param bytes バイト数
 * @return フォーマットされた文字列（例: "1.5 MB", "500 bytes"）
 */
public fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "0 bytes"
    if (bytes < 1024) return "$bytes bytes"

    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = minOf(
        (log10(bytes.toDouble()) / log10(1024.0)).toInt(),
        units.size,
    )

    val size = bytes / 1024.0.pow(digitGroups.toDouble())

    return "%.1f %s".format(size, units[digitGroups - 1])
}
