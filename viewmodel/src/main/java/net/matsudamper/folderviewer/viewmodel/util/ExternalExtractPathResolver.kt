package net.matsudamper.folderviewer.viewmodel.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File

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

    internal fun resolveQueriedFile(
        queryColumn: (String) -> String?,
        volumeDirectory: (String) -> File?,
    ): File? {
        val dataFile = readableFile(queryColumn(MediaStore.MediaColumns.DATA))
        if (dataFile != null) {
            return dataFile
        }
        val volumeName = queryColumn(MediaStore.MediaColumns.VOLUME_NAME) ?: return null
        val root = volumeDirectory(volumeName) ?: return null
        val relativePath = queryColumn(MediaStore.MediaColumns.RELATIVE_PATH) ?: return null
        val name = queryColumn(MediaStore.MediaColumns.DISPLAY_NAME) ?: return null
        val containsPathSeparator = name.any { it == '/' || it == '\\' }
        if (name.isBlank() || name in listOf(".", "..") || containsPathSeparator) {
            return null
        }
        val filePath = if (relativePath.isEmpty()) name else "$relativePath/$name"
        return runCatching { resolveRelativeFile(root, filePath) }.getOrNull()
    }

    internal fun readableFile(path: String?): File? {
        if (path.isNullOrBlank()) {
            return null
        }
        return File(path).takeIf { it.isAbsolute && it.isFile && it.canRead() }
    }

    internal fun resolveRelativeFile(root: File, relativePath: String): File? {
        if (File(relativePath).isAbsolute) {
            return null
        }
        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, relativePath).canonicalFile
        if (!file.toPath().startsWith(canonicalRoot.toPath())) {
            return null
        }
        return readableFile(file.path)
    }

    internal fun copyWithLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        maxBytes: Long,
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
                error("コピー先のストレージ容量が不足しています")
            }
            output.write(buffer, 0, readBytes)
        }
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
                readableFile(path)
            }

            "content" -> runCatching { resolveDocumentUri(context, uri) }.getOrNull()
                ?: runCatching { resolveProviderFile(context, uri) }.getOrNull()

            else -> null
        }
    }

    private fun resolveDocumentUri(context: Context, uri: Uri): File? {
        if (!DocumentsContract.isDocumentUri(context, uri)) {
            return null
        }
        val docId = DocumentsContract.getDocumentId(uri)
        if (docId.startsWith("raw:")) {
            return readableFile(docId.removePrefix("raw:"))
        }
        if (uri.authority == "com.android.providers.downloads.documents") {
            return resolveDownloadFile(context, docId)
        }
        if (uri.authority != "com.android.externalstorage.documents") {
            return null
        }
        val split = docId.split(':', limit = 2)
        if (split.size != 2) {
            return null
        }
        val type = split[0]
        val relativePath = split[1]
        val root = resolveVolumeDirectory(context, type) ?: return null
        return resolveRelativeFile(root, relativePath)
    }

    private fun resolveDownloadFile(context: Context, documentId: String): File? {
        if (documentId.startsWith("msf:") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val id = documentId.removePrefix("msf:").toLongOrNull() ?: return null
            val downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            return resolveQueriedFile(context, ContentUris.withAppendedId(downloadsUri, id))
        }
        val id = documentId.toLongOrNull() ?: return null
        return sequenceOf("public_downloads", "all_downloads").mapNotNull { collection ->
            val downloadUri = ContentUris.withAppendedId(Uri.parse("content://downloads/$collection"), id)
            resolveQueriedFile(context, downloadUri) ?: queryString(context, downloadUri, "mediaprovider_uri")
                ?.let { resolveMediaStoreFile(context, Uri.parse(it)) }
        }.firstOrNull()
    }

    private fun resolveMediaStoreFile(context: Context, uri: Uri): File? {
        if (uri.authority != MediaStore.AUTHORITY) {
            return null
        }
        return resolveQueriedFile(context, uri)
    }

    private fun resolveProviderFile(context: Context, uri: Uri): File? {
        if (uri.authority == MediaStore.AUTHORITY) {
            return resolveQueriedFile(context, uri)
        }
        val file = resolveQueriedFile(context, uri) ?: return null
        val sharedName = queryString(context, uri, OpenableColumns.DISPLAY_NAME)
        val sharedSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        return file.takeIf { matchesSharedContent(it, sharedName, sharedSize) }
    }

    internal fun matchesSharedContent(file: File, sharedName: String?, sharedSize: Long?): Boolean {
        if (sharedName == null || sharedSize == null || sharedSize < 0) {
            return false
        }
        return file.name == sharedName && file.length() == sharedSize
    }

    private fun resolveQueriedFile(context: Context, uri: Uri): File? {
        return resolveQueriedFile(
            queryColumn = { column ->
                if (column == MediaStore.MediaColumns.DATA || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryString(context, uri, column)
                } else {
                    null
                }
            },
            volumeDirectory = { resolveVolumeDirectory(context, it) },
        )
    }

    private fun queryString(context: Context, uri: Uri, column: String): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    private fun resolveVolumeDirectory(context: Context, volumeName: String): File? {
        if (volumeName.equals("primary", ignoreCase = true) || volumeName == "external_primary") {
            return Environment.getExternalStorageDirectory()
        }
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return null
        val volume = storageManager.storageVolumes
            .firstOrNull { it.uuid.equals(volumeName, ignoreCase = true) } ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory
        } else {
            context.getExternalFilesDirs(null)
                .filterNotNull()
                .firstOrNull { storageManager.getStorageVolume(it) == volume }
                ?.absolutePath
                ?.substringBefore("/Android/")
                ?.let { File(it) }
        }
    }

    private fun copyToFallbackLocation(
        context: Context,
        uri: Uri,
        fileName: String,
    ): ResolvedExtractFile? {
        var sourceFile: File? = null
        return runCatching {
            val outputDirectory = fallbackDocumentsDirectory()
            outputDirectory.mkdirs()
            val stagingDirectory = ExternalExtractStagingSupport.stagingDirectory(context.cacheDir).apply { mkdirs() }
            ExternalExtractStagingSupport.deleteExpiredStagedSources(context.cacheDir, System.currentTimeMillis())
            sourceFile = File.createTempFile("source-", null, stagingDirectory)
            val stagedFile = sourceFile
            val inputStream = context.contentResolver.openInputStream(uri) ?: error("input stream unavailable")
            inputStream.use { input ->
                stagedFile.outputStream().use { output ->
                    copyWithLimit(input, output, maxBytes = stagingDirectory.usableSpace)
                }
            }
            if (!stagedFile.isFile) {
                error("invalid file")
            }
            ResolvedExtractFile(
                sourceFile = stagedFile,
                outputParentPath = outputDirectory.absolutePath,
                fileName = fileName,
                usedFallbackOutputLocation = true,
            )
        }.onFailure {
            sourceFile?.delete()
        }.getOrNull()
    }

    private fun fallbackDocumentsDirectory(): File {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documents, "FolderViewer")
    }
}
