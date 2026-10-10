package net.matsudamper.folderviewer.viewmodel.textviewer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream
import java.net.URL
import net.matsudamper.folderviewer.viewmodel.util.TextFileDecoder
import net.matsudamper.folderviewer.viewmodel.util.TextLineEnding
import net.matsudamper.folderviewer.viewmodel.util.TextLineEndingDetector

internal class TextViewerDocumentLoader(
    private val contentResolver: ContentResolver,
    private val defaultTitle: String,
) {
    fun load(uri: Uri): Result {
        val title = resolveTitle(uri)
        val payload = try {
            openInputStream(uri)?.use { input ->
                TextFileDecoder.readPayload(input)
            }
        } catch (_: IOException) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        } catch (_: SecurityException) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        }
        if (payload == null) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        }
        return when (payload) {
            TextFileDecoder.Payload.TooLarge -> Result.Failure(title = title, reason = Result.Reason.TooLarge)

            is TextFileDecoder.Payload.Ready -> when (val decoded = payload.decoded) {
                is TextFileDecoder.Result.Text -> Result.Success(
                    title = title,
                    bytes = payload.bytes,
                    text = decoded.text,
                    encoding = decoded.encoding,
                    lineEnding = TextLineEndingDetector.detect(decoded.text),
                )

                TextFileDecoder.Result.Binary -> Result.Success(
                    title = title,
                    bytes = payload.bytes,
                    text = null,
                    encoding = null,
                    lineEnding = null,
                )

                TextFileDecoder.Result.TooLarge -> Result.Failure(title = title, reason = Result.Reason.TooLarge)
            }
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        val scheme = uri.scheme
        if (scheme == "http" || scheme == "https") {
            val connection = URL(uri.toString()).openConnection()
            connection.connectTimeout = HttpTimeoutMillis
            connection.readTimeout = HttpTimeoutMillis
            return connection.getInputStream()
        }
        return contentResolver.openInputStream(uri)
    }

    private fun resolveTitle(uri: Uri): String {
        val queried = queryDisplayName(uri)
        if (!queried.isNullOrBlank()) return queried
        val segment = uri.lastPathSegment.orEmpty()
        val name = segment.substringAfterLast('/').substringAfterLast(':')
        return name.ifBlank { defaultTitle }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) return null
                cursor.getString(index)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    sealed interface Result {
        data class Success(
            val title: String,
            val bytes: TextFileDecoder.FileBytes,
            val text: String?,
            val encoding: TextFileDecoder.TextEncoding?,
            val lineEnding: TextLineEnding?,
        ) : Result

        data class Failure(
            val title: String,
            val reason: Reason,
        ) : Result

        enum class Reason {
            TooLarge,
            Unreadable,
        }
    }

    private companion object {
        const val HttpTimeoutMillis: Int = 15_000
    }
}
