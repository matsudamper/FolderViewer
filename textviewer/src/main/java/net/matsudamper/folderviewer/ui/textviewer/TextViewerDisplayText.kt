package net.matsudamper.folderviewer.ui.textviewer

internal class TextViewerDisplayText private constructor(
    val text: String,
    private val displayOffsets: IntArray,
) {
    fun toDisplayOffset(originalOffset: Int): Int {
        if (originalOffset <= 0) return 0
        if (originalOffset >= displayOffsets.size) return text.length
        return displayOffsets[originalOffset]
    }

    fun lineNumberLabels(): String {
        return formatLineNumberLabels(visualLineCounts = null)
    }

    fun lineNumberLabels(visualLineCounts: IntArray): String {
        return formatLineNumberLabels(visualLineCounts = visualLineCounts)
    }

    fun visualLineCounts(lineForOffset: (Int) -> Int): IntArray {
        if (text.isEmpty()) return intArrayOf(1)
        return buildList {
            var lineStart = 0
            var index = 0
            while (index < text.length) {
                if (text[index] == '\n') {
                    add(visualLineCount(lineStart, index, lineForOffset))
                    lineStart = index + 1
                }
                index += 1
            }
            if (lineStart >= text.length) {
                add(1)
            } else {
                add(visualLineCount(lineStart, text.lastIndex, lineForOffset))
            }
        }.toIntArray()
    }

    private fun formatLineNumberLabels(visualLineCounts: IntArray?): String {
        val lines = text.split('\n')
        val width = lines.size.toString().length
        return lines.indices.joinToString(separator = "\n") { index ->
            val label = (index + 1).toString().padStart(width)
            val visualCount = visualLineCounts?.getOrElse(index) { 1 } ?: 1
            val extraBreaks = (visualCount - 1).coerceAtLeast(0)
            if (extraBreaks == 0) label else label + "\n".repeat(extraBreaks)
        }
    }

    private fun visualLineCount(
        start: Int,
        endInclusive: Int,
        lineForOffset: (Int) -> Int,
    ): Int {
        val startLine = lineForOffset(start)
        val endLine = lineForOffset(endInclusive)
        return (endLine - startLine + 1).coerceAtLeast(1)
    }

    companion object {
        fun from(source: String): TextViewerDisplayText {
            val displayOffsets = IntArray(source.length + 1)
            val display = StringBuilder(source.length)
            var index = 0
            while (index < source.length) {
                displayOffsets[index] = display.length
                val current = source[index]
                val nextIsLf = index + 1 < source.length && source[index + 1] == '\n'
                if (current == '\r' && nextIsLf) {
                    displayOffsets[index + 1] = display.length
                    display.append('\n')
                    index += 2
                } else if (current == '\r') {
                    display.append('\n')
                    index += 1
                } else {
                    display.append(current)
                    index += 1
                }
            }
            displayOffsets[source.length] = display.length
            return TextViewerDisplayText(
                text = display.toString(),
                displayOffsets = displayOffsets,
            )
        }
    }
}
