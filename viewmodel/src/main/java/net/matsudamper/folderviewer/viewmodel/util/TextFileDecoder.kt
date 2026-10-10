package net.matsudamper.folderviewer.viewmodel.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object TextFileDecoder {
    const val MaxBytes: Int = 8 * 1024 * 1024

    private val fallbackCharset: Charset = runCatching { charset("MS932") }.getOrDefault(Charsets.ISO_8859_1)

    fun read(input: InputStream): Result {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MaxBytes) return Result.TooLarge
            output.write(buffer, 0, count)
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): Result {
        if (bytes.isEmpty()) return Result.Text("")
        val utf16 = decodeUtf16Bom(bytes)
        if (utf16 != null) return Result.Text(utf16)
        val payload = stripUtf8Bom(bytes)
        if (containsNul(payload)) return Result.Binary
        val utf8 = decodeUtf8(payload)
        if (utf8 != null) return Result.Text(utf8)
        return Result.Text(String(payload, fallbackCharset))
    }

    private fun decodeUtf16Bom(bytes: ByteArray): String? {
        if (bytes.size < 2) return null
        val charset = when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> return null
        }
        return String(bytes, 2, bytes.size - 2, charset)
    }

    private fun stripUtf8Bom(bytes: ByteArray): ByteArray {
        val hasBom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        if (!hasBom) return bytes
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

    sealed interface Result {
        data class Text(val text: String) : Result

        data object TooLarge : Result

        data object Binary : Result
    }
}
