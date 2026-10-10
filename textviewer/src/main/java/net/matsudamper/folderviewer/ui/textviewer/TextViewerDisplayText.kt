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
        val lines = text.split('\n')
        val width = lines.size.toString().length
        return lines.indices.joinToString(separator = "\n") { index ->
            (index + 1).toString().padStart(width)
        }
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
