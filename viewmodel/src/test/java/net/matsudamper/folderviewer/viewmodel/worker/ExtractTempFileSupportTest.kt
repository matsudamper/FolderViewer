package net.matsudamper.folderviewer.viewmodel.worker

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ExtractTempFileSupportTest {
    @Test
    fun publishTempFile_renamesWithinOutputDirectory() {
        val root = Files.createTempDirectory("extract-temp-support").toFile()
        try {
            val outputDir = File(root, "output").apply { mkdirs() }
            val tempFile = File.createTempFile("extract-", ".tmp", outputDir)
            tempFile.writeText("payload")
            val outputFile = File(outputDir, "result.bin")

            ExtractTempFileSupport.publishTempFile(tempFile, outputFile)

            assertEquals("payload", outputFile.readText())
            assertTrue(!tempFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
