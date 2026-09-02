package net.matsudamper.folderviewer.viewmodel.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

@Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")
internal class ExternalExtractPathResolverTest {
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
