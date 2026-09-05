package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.nio.file.Files
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
    fun findConflict_reportsExistingFileWhenZipOutputNameConflicts() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            File(root, "archive").apply { writeText("duplicate") }
            val conflict = ExtractOutputConflictChecker.conflictMessage(
                parentPath = root.absolutePath,
                outputName = "archive",
            )
            assertEquals(
                "同じ名前のファイルが既に存在します: archive",
                conflict,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findConflict_reportsExistingFolderWhenCompressedOutputNameConflicts() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            File(root, "app").apply { mkdir() }
            val conflict = ExtractOutputConflictChecker.conflictMessage(
                parentPath = root.absolutePath,
                outputName = "app",
            )
            assertEquals(
                "同じ名前のフォルダが既に存在します: app",
                conflict,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findConflict_detectsExistingFileForTarOutputName() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            val existingFile = File(root, "my-output").apply { writeText("duplicate") }
            val conflict = ExtractOutputConflictChecker.conflictMessage(
                parentPath = root.absolutePath,
                outputName = existingFile.name,
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
    fun findConflictForCompressedOutput_detectsApkCandidateConflict() {
        val root = Files.createTempDirectory("extract-output-conflict").toFile()
        try {
            File(root, "app.apk").apply { writeText("duplicate") }
            val conflict = ExtractOutputConflictChecker.findConflictForCompressedOutput(
                parentPath = root.absolutePath,
                outputName = "app.xz",
            )
            assertEquals(
                "同じ名前のファイルが既に存在します: app.apk",
                conflict?.message,
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
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
