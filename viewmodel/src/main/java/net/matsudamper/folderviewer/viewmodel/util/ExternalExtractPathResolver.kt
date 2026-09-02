package net.matsudamper.folderviewer.viewmodel.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.UUID

internal object ExternalExtractPathResolver {
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
        val directory = File(context.cacheDir, "external_extract/${UUID.randomUUID()}").apply { mkdirs() }
        val sourceFile = File(directory, fileName)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        return runCatching {
            inputStream.use { input ->
                sourceFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (!sourceFile.isFile) {
                error("invalid file")
            }
            ResolvedExtractFile(
                sourceFile = sourceFile,
                outputParentPath = directory.absolutePath,
                fileName = fileName,
                usedFallbackOutputLocation = true,
            )
        }.onFailure {
            directory.deleteRecursively()
        }.getOrNull()
    }
}
