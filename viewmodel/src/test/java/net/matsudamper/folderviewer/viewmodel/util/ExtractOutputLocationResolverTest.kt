package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.nio.file.Files
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ExtractOutputLocationResolverTest {
    @Test
    fun isDuplicateOutputError_detectsDuplicateMessages() {
        assertTrue(
            ExtractOutputLocationResolver.isDuplicateOutputError(
                "同じ名前のファイルが既に存在します: app.apk",
            ),
        )
        assertTrue(
            ExtractOutputLocationResolver.isDuplicateOutputError(
                "同じ名前のフォルダが既に存在します: archive",
            ),
        )
        assertFalse(ExtractOutputLocationResolver.isDuplicateOutputError("出力ファイルの作成に失敗しました"))
        assertFalse(ExtractOutputLocationResolver.isDuplicateOutputError(null))
    }

    @Test
    fun parseDuplicateOutputName_extractsOutputName() {
        assertEquals(
            "app.apk",
            ExtractOutputLocationResolver.parseDuplicateOutputName(
                "同じ名前のファイルが既に存在します: app.apk",
            ),
        )
        assertEquals(
            "archive",
            ExtractOutputLocationResolver.parseDuplicateOutputName(
                "同じ名前のフォルダが既に存在します: archive",
            ),
        )
        assertNull(ExtractOutputLocationResolver.parseDuplicateOutputName("解凍に失敗しました"))
    }

    @Test
    fun resolveExistingOutputPath_usesDuplicateNameFromErrorMessage() {
        val root = Files.createTempDirectory("extract-output-location").toFile()
        try {
            val outputDir = File(root, "output").apply { mkdirs() }
            val duplicateFile = File(outputDir, "app.apk").apply { writeText("duplicate") }
            val meta = createMeta(
                localFolderPath = outputDir.absolutePath,
                outputName = "app",
            )

            val resolvedPath = ExtractOutputLocationResolver.resolveExistingOutputPath(
                meta = meta,
                errorMessage = "同じ名前のファイルが既に存在します: ${duplicateFile.name}",
            )

            assertEquals(duplicateFile.absolutePath, resolvedPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveExistingOutputPath_fallsBackToOutputNameWhenFileExists() {
        val root = Files.createTempDirectory("extract-output-location").toFile()
        try {
            val outputDir = File(root, "output").apply { mkdirs() }
            val existingFolder = File(outputDir, "archive").apply { mkdir() }
            val meta = createMeta(
                localFolderPath = outputDir.absolutePath,
                outputName = existingFolder.name,
            )

            val resolvedPath = ExtractOutputLocationResolver.resolveExistingOutputPath(
                meta = meta,
                errorMessage = "同じ名前のフォルダが既に存在します: ${existingFolder.name}",
            )

            assertEquals(existingFolder.absolutePath, resolvedPath)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createMeta(
        localFolderPath: String,
        outputName: String,
        outputAbsolutePath: String? = null,
        sourceAbsolutePath: String = "/tmp/source.zip",
    ): ExtractJobRepository.ExtractJobMeta {
        return ExtractJobRepository.ExtractJobMeta(
            id = 1L,
            sourceFileObjectId = null,
            sourceFileName = "source.zip",
            outputName = outputName,
            extractType = ExtractJobRepository.ExtractType.Zip,
            parentFileObjectId = null,
            parentDisplayPath = "",
            localFolderPath = localFolderPath,
            openOnComplete = false,
            openOnCompleteHandled = false,
            outputAbsolutePath = outputAbsolutePath,
            sourceAbsolutePath = sourceAbsolutePath,
        )
    }
}
