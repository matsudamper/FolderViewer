package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.nio.file.Files
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ExtractOutputConflictCheckerTest {
    @Test
    fun findConflict_detectsExistingFolder() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            val existingFolder = File(root, "archive").apply { mkdir() }
            val conflict = ExtractOutputConflictChecker.findConflict(
                parentPath = root.absolutePath,
                outputName = existingFolder.name,
                outputKind = ExtractOutputConflictChecker.OutputKind.Folder,
            )
            assertTrue(conflict is ExtractOutputConflictChecker.Conflict.FolderExists)
            assertEquals(
                "同じ名前のフォルダが既に存在します: archive",
                conflict?.message,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findConflict_detectsExistingFile() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            val existingFile = File(root, "app.apk").apply { writeText("duplicate") }
            val conflict = ExtractOutputConflictChecker.findConflict(
                parentPath = root.absolutePath,
                outputName = existingFile.name,
                outputKind = ExtractOutputConflictChecker.OutputKind.File,
            )
            assertTrue(conflict is ExtractOutputConflictChecker.Conflict.FileExists)
            assertEquals(
                "同じ名前のファイルが既に存在します: app.apk",
                conflict?.message,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findAnyConflict_detectsExistingFileForTarTypes() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            val existingFile = File(root, "my-output").apply { writeText("duplicate") }
            val conflict = ExtractOutputConflictChecker.conflictMessage(
                parentPath = root.absolutePath,
                outputName = existingFile.name,
                extractType = ExtractableFileType.TarGz,
            )
            assertEquals(
                "同じ名前のファイルが既に存在します: my-output",
                conflict,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun conflictMessage_returnsNullWhenOutputDoesNotExist() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            assertNull(
                ExtractOutputConflictChecker.conflictMessage(
                    parentPath = root.absolutePath,
                    outputName = "archive",
                    extractType = ExtractJobRepository.ExtractType.Zip,
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
