package net.matsudamper.folderviewer.viewmodel.textviewer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream
import java.net.URL
import net.matsudamper.folderviewer.viewmodel.util.TextFileDecoder

internal class TextViewerDocumentLoader(
    private val contentResolver: ContentResolver,
    private val defaultTitle: String,
) {
    fun load(uri: Uri): Result {
        val title = resolveTitle(uri)
        val decoded = try {
            openInputStream(uri)?.use { input ->
                TextFileDecoder.read(input)
            }
        } catch (_: IOException) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        } catch (_: SecurityException) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        }
        if (decoded == null) {
            return Result.Failure(title = title, reason = Result.Reason.Unreadable)
        }
        return when (decoded) {
            is TextFileDecoder.Result.Text -> Result.Success(title = title, text = decoded.text)
            TextFileDecoder.Result.TooLarge -> Result.Failure(title = title, reason = Result.Reason.TooLarge)
            TextFileDecoder.Result.Binary -> Result.Failure(title = title, reason = Result.Reason.Binary)
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        val scheme = uri.scheme
        if (scheme == "http" || scheme == "https") {
            return URL(uri.toString()).openStream()
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
            val text: String,
        ) : Result

        data class Failure(
            val title: String,
            val reason: Reason,
        ) : Result

        enum class Reason {
            TooLarge,
            Binary,
            Unreadable,
        }
    }
}
