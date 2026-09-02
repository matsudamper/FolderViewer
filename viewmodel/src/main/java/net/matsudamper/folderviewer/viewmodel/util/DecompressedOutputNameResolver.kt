package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.io.FileInputStream

internal object DecompressedOutputNameResolver {
    fun resolveFileName(outputName: String, decompressedFile: File): String {
        if (outputName.endsWith(".apk", ignoreCase = true)) {
            return outputName
        }
        if (!isZipMagic(decompressedFile)) {
            return outputName
        }
        return if (outputName.contains('.')) {
            val baseName = outputName.substringBeforeLast('.')
            if (baseName.endsWith(".apk", ignoreCase = true)) {
                outputName
            } else {
                "$baseName.apk"
            }
        } else {
            "$outputName.apk"
        }
    }

    private fun isZipMagic(file: File): Boolean {
        val header = ByteArray(4)
        FileInputStream(file).use { input ->
            if (input.read(header) < 4) {
                return false
            }
        }
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }
}
