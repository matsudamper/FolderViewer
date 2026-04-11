package net.matsudamper.folderviewer.repository

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableFileWriterTest {
    @Test
    fun resumesFromPartialFile() {
        runTest {
            val directory = createTempDirectory().toFile()
            val sourceBytes = "0123456789".toByteArray()
            val destinationFile = File(directory, "target.bin")
            val partialFile = File(directory, ".target.part")
            partialFile.writeBytes(sourceBytes.copyOfRange(0, 4))
            val offsets = mutableListOf<Long>()

            val writer = ResumableFileWriter(
                destinationFile = destinationFile,
                partialFile = partialFile,
                sourceSize = sourceBytes.size.toLong(),
                overwrite = false,
                openInputStream = { offset ->
                    offsets += offset
                    ByteArrayInputStream(sourceBytes.copyOfRange(offset.toInt(), sourceBytes.size))
                },
                onProgress = {},
                shouldStop = { false },
            )

            val completed = writer.copy()

            assertTrue(completed)
            assertEquals(listOf(4L), offsets)
            assertArrayEquals(sourceBytes, destinationFile.readBytes())
            assertFalse(partialFile.exists())
            directory.deleteRecursively()
        }
    }

    @Test
    fun retriesFromLastWrittenOffset() {
        runTest {
            val directory = createTempDirectory().toFile()
            val sourceBytes = "abcdefghijklmnop".toByteArray()
            val destinationFile = File(directory, "target.bin")
            val partialFile = File(directory, ".target.part")
            val offsets = mutableListOf<Long>()
            var openCount = 0

            val writer = ResumableFileWriter(
                destinationFile = destinationFile,
                partialFile = partialFile,
                sourceSize = sourceBytes.size.toLong(),
                overwrite = false,
                openInputStream = { offset ->
                    offsets += offset
                    val bytes = sourceBytes.copyOfRange(offset.toInt(), sourceBytes.size)
                    if (openCount++ == 0) {
                        FailingInputStream(bytes, failAfter = 5)
                    } else {
                        ByteArrayInputStream(bytes)
                    }
                },
                onProgress = {},
                shouldStop = { false },
            )

            val completed = writer.copy()

            assertTrue(completed)
            assertEquals(listOf(0L, 5L), offsets)
            assertArrayEquals(sourceBytes, destinationFile.readBytes())
            assertFalse(partialFile.exists())
            directory.deleteRecursively()
        }
    }

    @Test
    fun keepsExistingDestinationWhenOverwriteIsFalse() {
        runTest {
            val directory = createTempDirectory().toFile()
            val destinationFile = File(directory, "target.bin")
            val partialFile = File(directory, ".target.part")
            destinationFile.writeText("existing")

            val writer = ResumableFileWriter(
                destinationFile = destinationFile,
                partialFile = partialFile,
                sourceSize = 3L,
                overwrite = false,
                openInputStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
                onProgress = {},
                shouldStop = { false },
            )

            val exception = runCatching { writer.copy() }.exceptionOrNull()
            assertTrue(exception is FileAlreadyExistsException)
            assertEquals("existing", destinationFile.readText())
            assertFalse(partialFile.exists())
            directory.deleteRecursively()
        }
    }

    private class FailingInputStream(
        private val bytes: ByteArray,
        private val failAfter: Int,
    ) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index >= failAfter) throw IOException("failed")
            if (index >= bytes.size) return -1
            return bytes[index++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (index >= failAfter) throw IOException("failed")
            if (index >= bytes.size) return -1
            val count = minOf(length, bytes.size - index, failAfter - index)
            bytes.copyInto(buffer, offset, index, index + count)
            index += count
            return count
        }
    }
}
