package net.matsudamper.folderviewer.viewmodel.util

import org.junit.Assert.assertEquals
import org.junit.Test

internal class TextLineEndingDetectorTest {
    @Test
    fun detect_lf() {
        assertEquals(TextLineEnding.Lf, TextLineEndingDetector.detect("a\nb\n"))
    }

    @Test
    fun detect_crlf() {
        assertEquals(TextLineEnding.CrLf, TextLineEndingDetector.detect("a\r\nb\r\n"))
    }

    @Test
    fun detect_cr() {
        assertEquals(TextLineEnding.Cr, TextLineEndingDetector.detect("a\rb\r"))
    }

    @Test
    fun detect_mixed() {
        assertEquals(TextLineEnding.Mixed, TextLineEndingDetector.detect("a\nb\r\n"))
    }

    @Test
    fun detect_noneWhenSingleLine() {
        assertEquals(null, TextLineEndingDetector.detect("hello"))
    }
}
