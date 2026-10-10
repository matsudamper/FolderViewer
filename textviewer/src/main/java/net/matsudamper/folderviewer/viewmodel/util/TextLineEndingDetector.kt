package net.matsudamper.folderviewer.viewmodel.util

internal object TextLineEndingDetector {
    fun detect(text: String): TextLineEnding? {
        var sawLf = false
        var sawCrLf = false
        var sawCr = false
        var index = 0
        while (index < text.length) {
            val current = text[index]
            val nextIsLf = index + 1 < text.length && text[index + 1] == '\n'
            if (current == '\r' && nextIsLf) {
                sawCrLf = true
                index += 2
            } else if (current == '\r') {
                sawCr = true
                index += 1
            } else if (current == '\n') {
                sawLf = true
                index += 1
            } else {
                index += 1
            }
        }
        return ending(sawLf = sawLf, sawCrLf = sawCrLf, sawCr = sawCr)
    }

    private fun ending(sawLf: Boolean, sawCrLf: Boolean, sawCr: Boolean): TextLineEnding? {
        val count = listOf(sawLf, sawCrLf, sawCr).count { present -> present }
        return when {
            count == 0 -> null
            count > 1 -> TextLineEnding.Mixed
            sawLf -> TextLineEnding.Lf
            sawCrLf -> TextLineEnding.CrLf
            else -> TextLineEnding.Cr
        }
    }
}

internal enum class TextLineEnding {
    Lf,
    CrLf,
    Cr,
    Mixed,
}
