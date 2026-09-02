package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

internal class DecompressedOutputNameResolverTest {
    @Test
    fun resolveFileName_keepsApkExtension() {
        val tempDir = Files.createTempDirectory("decompressed-output-name").toFile()
        try {
            val file = File(tempDir, "output")
            file.writeBytes(zipMagic())
            assertEquals("app.apk", DecompressedOutputNameResolver.resolveFileName("app.apk", file))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun resolveFileName_addsApkExtensionForZipMagic() {
        val tempDir = Files.createTempDirectory("decompressed-output-name").toFile()
        try {
            val file = File(tempDir, "output")
            file.writeBytes(zipMagic())
            assertEquals("foo.apk", DecompressedOutputNameResolver.resolveFileName("foo", file))
            assertEquals("foo.apk", DecompressedOutputNameResolver.resolveFileName("foo.xz", file))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun resolveFileName_keepsNonApkOutput() {
        val tempDir = Files.createTempDirectory("decompressed-output-name").toFile()
        try {
            val file = File(tempDir, "output")
            file.writeText("plain text")
            assertEquals("foo", DecompressedOutputNameResolver.resolveFileName("foo", file))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun zipMagic(): ByteArray {
        return byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }
}
