package net.matsudamper.folderviewer.viewmodel.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal object ExternalExtractPathResolver {
    private const val MAX_COPY_BYTES = 2L * 1024 * 1024 * 1024
    data class ResolvedExtractFile(
        val sourceFile: File,
        val outputParentPath: String,
        val fileName: String,
        val usedFallbackOutputLocation: Boolean,
    )

    fun resolve(context: Context, uri: Uri): ResolvedExtractFile? {
        val fileName = resolveFileName(context, uri) ?: return null
        if (ExtractableFileNameUtil.detect(fileName) == null) {
            return null
        }
        resolveAbsoluteFile(context, uri)?.let { sourceFile ->
            val parentPath = sourceFile.parentFile?.absolutePath ?: return null
            return ResolvedExtractFile(
                sourceFile = sourceFile,
                outputParentPath = parentPath,
                fileName = fileName,
                usedFallbackOutputLocation = false,
            )
        }
        return copyToFallbackLocation(context, uri, fileName)
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return DocumentFile.fromSingleUri(context, uri)?.name
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: uri.path
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
    }

    private fun resolveAbsoluteFile(context: Context, uri: Uri): File? {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return null
                File(path).takeIf { it.isFile }
            }

            "content" -> resolveDocumentUri(context, uri)

            else -> null
        }
    }

    private fun resolveDocumentUri(context: Context, uri: Uri): File? {
        if (!DocumentsContract.isDocumentUri(context, uri)) {
            return null
        }
        val docId = DocumentsContract.getDocumentId(uri)
        if (docId.startsWith("raw:")) {
            return File(docId.removePrefix("raw:")).takeIf { it.isFile }
        }
        val split = docId.split(':', limit = 2)
        if (split.size != 2) {
            return null
        }
        val type = split[0]
        val relativePath = split[1]
        if (type.equals("primary", ignoreCase = true)) {
            return File(Environment.getExternalStorageDirectory(), relativePath).takeIf { it.isFile }
        }
        return null
    }

    private fun copyToFallbackLocation(
        context: Context,
        uri: Uri,
        fileName: String,
    ): ResolvedExtractFile? {
        val outputDirectory = fallbackDocumentsDirectory()
        outputDirectory.mkdirs()
        val stagingDirectory = File(context.cacheDir, "external-extract-source").apply { mkdirs() }
        val sourceFile = File.createTempFile("source-", null, stagingDirectory)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        return runCatching {
            inputStream.use { input ->
                sourceFile.outputStream().use { output ->
                    copyWithLimit(input, output)
                }
            }
            if (!sourceFile.isFile) {
                error("invalid file")
            }
            ResolvedExtractFile(
                sourceFile = sourceFile,
                outputParentPath = outputDirectory.absolutePath,
                fileName = fileName,
                usedFallbackOutputLocation = true,
            )
        }.onFailure {
            sourceFile.delete()
        }.getOrNull()
    }

    internal fun copyWithLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        maxBytes: Long = MAX_COPY_BYTES,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copiedBytes = 0L
        while (true) {
            val readBytes = input.read(buffer)
            if (readBytes == -1) {
                return
            }
            copiedBytes += readBytes
            if (copiedBytes > maxBytes) {
                error("コピーサイズが上限を超えています")
            }
            output.write(buffer, 0, readBytes)
        }
    }

    private fun fallbackDocumentsDirectory(): File {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documents, "FolderViewer")
    }
}
