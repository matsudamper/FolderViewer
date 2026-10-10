package net.matsudamper.folderviewer.viewmodel.util

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TextFileDecoderTest {
    @Test
    fun decode_readsUtf8() {
        val decoded = TextFileDecoder.decode("hello あ".toByteArray(Charsets.UTF_8))
        assertEquals(TextFileDecoder.Result.Text("hello あ"), decoded)
    }

    @Test
    fun decode_stripsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray(Charsets.UTF_8)
        assertEquals(TextFileDecoder.Result.Text("abc"), TextFileDecoder.decode(bytes))
    }

    @Test
    fun decode_readsUtf16LeBom() {
        val payload = "A".toByteArray(Charsets.UTF_16LE)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + payload
        assertEquals(TextFileDecoder.Result.Text("A"), TextFileDecoder.decode(bytes))
    }

    @Test
    fun decode_readsShiftJisWhenUtf8IsInvalid() {
        val bytes = "テスト".toByteArray(Charset.forName("MS932"))
        assertEquals(TextFileDecoder.Result.Text("テスト"), TextFileDecoder.decode(bytes))
    }

    @Test
    fun decode_rejectsNulAsBinary() {
        assertEquals(TextFileDecoder.Result.Binary, TextFileDecoder.decode(byteArrayOf(0x61, 0x00)))
    }

    @Test
    fun decode_emptyIsEmptyText() {
        assertEquals(TextFileDecoder.Result.Text(""), TextFileDecoder.decode(byteArrayOf()))
    }

    @Test
    fun read_returnsTooLargeWhenStreamExceedsLimit() {
        val input = RepeatingByteStream(remaining = TextFileDecoder.MaxBytes + 1, value = 'a'.code.toByte())
        assertEquals(TextFileDecoder.Result.TooLarge, TextFileDecoder.read(input))
    }

    @Test
    fun read_decodesStreamWithinLimit() {
        val input = ByteArrayInputStream("line".toByteArray(Charsets.UTF_8))
        assertEquals(TextFileDecoder.Result.Text("line"), TextFileDecoder.read(input))
    }

    @Test
    fun read_acceptsFileAtExactLimit() {
        val input = RepeatingByteStream(remaining = TextFileDecoder.MaxBytes, value = 'a'.code.toByte())
        val decoded = TextFileDecoder.read(input)
        assertTrue(decoded is TextFileDecoder.Result.Text)
        assertEquals(TextFileDecoder.MaxBytes, (decoded as TextFileDecoder.Result.Text).text.length)
    }

    private class RepeatingByteStream(
        private var remaining: Int,
        private val value: Byte,
    ) : InputStream() {
        override fun read(): Int {
            if (remaining == 0) return -1
            remaining -= 1
            return value.toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining == 0) return -1
            val count = minOf(len, remaining)
            b.fill(value, off, off + count)
            remaining -= count
            return count
        }
    }
}
