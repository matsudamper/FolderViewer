package net.matsudamper.folderviewer.ui.textviewer

import org.junit.Assert.assertEquals
import org.junit.Test

internal class TextViewerDisplayTextTest {
    @Test
    fun from_countsCarriageReturnAsLineBreak() {
        val display = TextViewerDisplayText.from("a\rb")
        assertEquals("a\nb", display.text)
        assertEquals("1\n2", display.lineNumberLabels())
    }

    @Test
    fun from_countsCrLfAsOneLineBreak() {
        val display = TextViewerDisplayText.from("a\r\nb")
        assertEquals("a\nb", display.text)
        assertEquals(1, display.toDisplayOffset(1))
        assertEquals(1, display.toDisplayOffset(2))
        assertEquals(2, display.toDisplayOffset(3))
        assertEquals("1\n2", display.lineNumberLabels())
    }

    @Test
    fun from_countsMixedBreaks() {
        val display = TextViewerDisplayText.from("a\nb\r\nc\rd")
        assertEquals("a\nb\nc\nd", display.text)
        assertEquals("1\n2\n3\n4", display.lineNumberLabels())
    }

    @Test
    fun lineNumberLabels_padsWidth() {
        val source = (1..10).joinToString(separator = "\n") { "x" }
        val display = TextViewerDisplayText.from(source)
        assertEquals(" 1", display.lineNumberLabels().lineSequence().first())
        assertEquals("10", display.lineNumberLabels().lineSequence().last())
    }
}
