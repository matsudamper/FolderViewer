package net.matsudamper.folderviewer.viewmodel.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object TextFileDecoder {
    const val MaxBytes: Int = 8 * 1024 * 1024

    fun read(input: InputStream): Result {
        return when (val payload = readPayload(input)) {
            Payload.TooLarge -> Result.TooLarge
            is Payload.Ready -> payload.decoded
        }
    }

    fun readPayload(input: InputStream): Payload {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MaxBytes) return Payload.TooLarge
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        return Payload.Ready(bytes = FileBytes(bytes), decoded = decode(bytes))
    }

    fun decode(bytes: ByteArray): Result {
        if (bytes.isEmpty()) return Result.Text(text = "", encoding = TextEncoding.Utf8)
        val bomEncoding = utfBomEncoding(bytes)
        if (bomEncoding != null) {
            return Result.Text(text = decodeWith(bytes, bomEncoding), encoding = bomEncoding)
        }
        if (containsNul(bytes)) return Result.Binary
        val utf8 = decodeUtf8(stripUtf8Bom(bytes))
        if (utf8 != null) return Result.Text(text = utf8, encoding = TextEncoding.Utf8)
        return Result.Text(text = decodeWith(bytes, TextEncoding.ShiftJis), encoding = TextEncoding.ShiftJis)
    }

    fun decodeWith(bytes: ByteArray, encoding: TextEncoding): String {
        val payload = stripBom(bytes, encoding)
        return String(payload, encoding.charset())
    }

    fun availableEncodings(): List<TextEncoding> {
        return TextEncoding.entries.filter { encoding ->
            runCatching { charset(encoding.charsetName) }.isSuccess
        }
    }

    private fun utfBomEncoding(bytes: ByteArray): TextEncoding? {
        if (startsWith(bytes, byteArrayOf(0xFF.toByte(), 0xFE.toByte()))) return TextEncoding.Utf16Le
        if (startsWith(bytes, byteArrayOf(0xFE.toByte(), 0xFF.toByte()))) return TextEncoding.Utf16Be
        if (startsWith(bytes, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) return TextEncoding.Utf8
        return null
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        return prefix.indices.all { index -> bytes[index] == prefix[index] }
    }

    private fun stripBom(bytes: ByteArray, encoding: TextEncoding): ByteArray {
        return when (encoding) {
            TextEncoding.Utf8 -> stripUtf8Bom(bytes)

            TextEncoding.Utf16Le -> stripByteOrderMark(bytes, 0xFF.toByte(), 0xFE.toByte())

            TextEncoding.Utf16Be -> stripByteOrderMark(bytes, 0xFE.toByte(), 0xFF.toByte())

            TextEncoding.ShiftJis,
            TextEncoding.EucJp,
            TextEncoding.Iso2022Jp,
            -> bytes
        }
    }

    private fun stripByteOrderMark(bytes: ByteArray, first: Byte, second: Byte): ByteArray {
        if (!startsWith(bytes, byteArrayOf(first, second))) return bytes
        return bytes.copyOfRange(2, bytes.size)
    }

    private fun stripUtf8Bom(bytes: ByteArray): ByteArray {
        if (!startsWith(bytes, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) return bytes
        return bytes.copyOfRange(3, bytes.size)
    }

    private fun containsNul(bytes: ByteArray): Boolean {
        return bytes.any { byte -> byte == 0.toByte() }
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    sealed interface Payload {
        data class Ready(
            val bytes: FileBytes,
            val decoded: Result,
        ) : Payload

        data object TooLarge : Payload
    }

    sealed interface Result {
        data class Text(
            val text: String,
            val encoding: TextEncoding,
        ) : Result

        data object TooLarge : Result

        data object Binary : Result
    }

    class FileBytes(val value: ByteArray)

    enum class TextEncoding(val label: String, val charsetName: String) {
        Utf8(label = "UTF-8", charsetName = "UTF-8"),
        Utf16Le(label = "UTF-16 LE", charsetName = "UTF-16LE"),
        Utf16Be(label = "UTF-16 BE", charsetName = "UTF-16BE"),
        ShiftJis(label = "Shift_JIS", charsetName = "MS932"),
        EucJp(label = "EUC-JP", charsetName = "EUC-JP"),
        Iso2022Jp(label = "ISO-2022-JP", charsetName = "ISO-2022-JP"),
        ;

        fun charset(): Charset {
            return runCatching { charset(charsetName) }.getOrDefault(Charsets.UTF_8)
        }
    }
}
