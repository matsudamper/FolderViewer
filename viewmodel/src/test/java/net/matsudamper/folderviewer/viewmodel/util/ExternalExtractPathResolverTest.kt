package net.matsudamper.folderviewer.viewmodel.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")
internal class ExternalExtractPathResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `DATAが読み取り可能ならボリューム情報なしでも採用する`() {
        val archive = temporaryFolder.newFile("archive.zip")

        assertEquals(
            archive,
            ExternalExtractPathResolver.resolveQueriedFile(
                queryColumn = { if (it == "_data") archive.path else null },
                volumeDirectory = { null },
            ),
        )
    }

    @Test
    fun `DATAが無効でもボリュームと相対パスから解決する`() {
        val directory = temporaryFolder.newFolder("Download")
        val archive = File(directory, "archive.zip").apply { writeText("archive") }
        val columns = mapOf(
            "_data" to File(directory, "missing.zip").path,
            "volume_name" to "1234-5678",
            "relative_path" to "Download/",
            "_display_name" to "archive.zip",
        )

        assertEquals(
            archive.canonicalFile,
            ExternalExtractPathResolver.resolveQueriedFile(
                queryColumn = { columns[it] },
                volumeDirectory = { if (it == "1234-5678") temporaryFolder.root else null },
            ),
        )
        assertNull(
            ExternalExtractPathResolver.resolveQueriedFile(
                queryColumn = { columns[it] },
                volumeDirectory = { null },
            ),
        )
    }

    @Test
    fun `照会結果が不足する場合は解決しない`() {
        assertNull(
            ExternalExtractPathResolver.resolveQueriedFile(
                queryColumn = { null },
                volumeDirectory = { temporaryFolder.root },
            ),
        )
    }

    @Test
    fun `表示名にパスが含まれる場合は採用しない`() {
        temporaryFolder.newFile("archive.zip")
        val columns = mapOf(
            "volume_name" to "external_primary",
            "relative_path" to "",
            "_display_name" to "nested/../archive.zip",
        )

        assertNull(
            ExternalExtractPathResolver.resolveQueriedFile(
                queryColumn = { columns[it] },
                volumeDirectory = { temporaryFolder.root },
            ),
        )
    }

    @Test
    fun `ボリューム直下のファイルを解決する`() {
        val archive = temporaryFolder.newFile("archive.zip")
        val columns = mapOf(
            "volume_name" to "external_primary",
            "relative_path" to "",
            "_display_name" to archive.name,
        )

        assertEquals(
            archive.canonicalFile,
            ExternalExtractPathResolver.resolveQueriedFile(
                queryColumn = { columns[it] },
                volumeDirectory = { temporaryFolder.root },
            ),
        )
    }

    @Test
    fun `実在するファイルを相対パスから解決する`() {
        val directory = temporaryFolder.newFolder("Download", "archives")
        val archive = File(directory, "日本語.zip").apply { writeText("archive") }

        assertEquals(
            archive.canonicalFile,
            ExternalExtractPathResolver.resolveRelativeFile(temporaryFolder.root, "Download/archives/日本語.zip"),
        )
    }

    @Test
    fun `存在しないファイルとディレクトリは採用しない`() {
        assertNull(ExternalExtractPathResolver.readableFile(File(temporaryFolder.root, "missing.zip").path))
        assertNull(ExternalExtractPathResolver.readableFile(temporaryFolder.root.path))
        assertNull(ExternalExtractPathResolver.readableFile("relative.zip"))
        assertNull(ExternalExtractPathResolver.readableFile(null))
    }

    @Test
    fun `ボリューム外へ出る相対パスは採用しない`() {
        val root = temporaryFolder.newFolder("volume")
        temporaryFolder.newFile("outside.zip")

        assertNull(ExternalExtractPathResolver.resolveRelativeFile(root, "../outside.zip"))
    }

    @Test
    fun `絶対パスを相対パスとして採用しない`() {
        val archive = temporaryFolder.newFile("archive.zip")

        assertNull(ExternalExtractPathResolver.resolveRelativeFile(temporaryFolder.root, archive.absolutePath))
    }

    @Test
    fun `上限以内の内容をコピーできる`() {
        val input = byteArrayOf(1, 2, 3)
        val output = ByteArrayOutputStream()

        ExternalExtractPathResolver.copyWithLimit(
            input = ByteArrayInputStream(input),
            output = output,
            maxBytes = input.size.toLong(),
        )

        assertArrayEquals(input, output.toByteArray())
    }

    @Test(expected = IllegalStateException::class)
    fun `上限を超える内容はコピーできない`() {
        ExternalExtractPathResolver.copyWithLimit(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            output = ByteArrayOutputStream(),
            maxBytes = 2,
        )
    }
}
