package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ExtractOutputNameValidatorTest {
    @Test
    fun validate_acceptsSimpleName() {
        assertTrue(ExtractOutputNameValidator.validate("archive") is ExtractOutputNameValidator.Result.Valid)
    }

    @Test
    fun validate_rejectsBlank() {
        assertEquals(ExtractOutputNameValidator.Result.Invalid, ExtractOutputNameValidator.validate(""))
        assertEquals(ExtractOutputNameValidator.Result.Invalid, ExtractOutputNameValidator.validate("   "))
    }

    @Test
    fun validate_rejectsDotNames() {
        assertEquals(ExtractOutputNameValidator.Result.Invalid, ExtractOutputNameValidator.validate("."))
        assertEquals(ExtractOutputNameValidator.Result.Invalid, ExtractOutputNameValidator.validate(".."))
    }

    @Test
    fun validate_rejectsPathTraversal() {
        assertEquals(ExtractOutputNameValidator.Result.Invalid, ExtractOutputNameValidator.validate("../escape"))
        assertEquals(ExtractOutputNameValidator.Result.Invalid, ExtractOutputNameValidator.validate("foo/../bar"))
    }

    @Test
    fun validate_rejectsAbsolutePath() {
        assertEquals(ExtractOutputNameValidator.Result.Invalid, ExtractOutputNameValidator.validate("/absolute"))
    }

    @Test
    fun resolveChildFile_returnsDirectChild() {
        val parent = Files.createTempDirectory("extract-output-name-validator").toFile()
        try {
            val child = ExtractOutputNameValidator.resolveChildFile(parent.absolutePath, "archive")
            assertNotNull(child)
            assertEquals(File(parent, "archive").absolutePath, child?.absolutePath)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun resolveChildFile_rejectsTraversal() {
        val parent = Files.createTempDirectory("extract-output-name-validator").toFile()
        try {
            assertNull(ExtractOutputNameValidator.resolveChildFile(parent.absolutePath, "../escape"))
            assertNull(ExtractOutputNameValidator.resolveChildFile(parent.absolutePath, "."))
            assertNull(ExtractOutputNameValidator.resolveChildFile(parent.absolutePath, ""))
        } finally {
            parent.deleteRecursively()
        }
    }
}
