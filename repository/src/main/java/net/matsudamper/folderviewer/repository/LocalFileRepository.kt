package net.matsudamper.folderviewer.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.matsudamper.folderviewer.common.FileObjectId

internal class LocalFileRepository(
    private val config: StorageConfiguration.Local,
) : FileRepository {
    override suspend fun getFiles(id: FileObjectId): List<FileItem> = withContext(Dispatchers.IO) {
        val path = when (id) {
            is FileObjectId.Root -> ""
            is FileObjectId.Item -> id.id
        }
        val targetDir = buildAbsoluteFile(path)

        if (!targetDir.exists() || !targetDir.canRead()) {
            return@withContext emptyList()
        }

        targetDir.listFiles()?.mapNotNull { file ->
            val relativePath = if (path.isEmpty()) {
                file.name
            } else {
                "$path/${file.name}"
            }

            FileItem(
                displayPath = file.name,
                id = FileObjectId.Item(relativePath),
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                lastModified = file.lastModified(),
            )
        }?.sortedWith(
            compareBy<FileItem> { !it.isDirectory }
                .thenBy { it.displayPath.lowercase() },
        ).orEmpty()
    }

    override suspend fun getFileContent(fileId: FileObjectId.Item): InputStream = withContext(Dispatchers.IO) {
        val file = buildAbsoluteFile(fileId.id)

        require(file.exists() && file.canRead()) { "File not found or cannot read: ${fileId.id}" }

        FileInputStream(file)
    }

    override suspend fun getThumbnail(fileId: FileObjectId.Item, thumbnailSize: Int): InputStream = withContext(Dispatchers.IO) {
        try {
            val file = buildAbsoluteFile(fileId.id)

            if (!file.exists() || !file.canRead()) {
                return@withContext getFileContent(fileId)
            }

            FileInputStream(file).use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                stream.use { BitmapFactory.decodeStream(it, null, options) }

                val width = options.outWidth
                val height = options.outHeight

                if (width <= 0 || height <= 0) {
                    return@withContext getFileContent(fileId)
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(minOf(width, height), thumbnailSize)
                }

                FileInputStream(file).use { decodeStream ->
                    val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                        ?: return@withContext getFileContent(fileId)

                    val bos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    bitmap.recycle()

                    ByteArrayInputStream(bos.toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getFileContent(fileId)
        }
    }

    private fun buildAbsoluteFile(path: String): File {
        return if (path.isEmpty()) {
            File(config.rootPath)
        } else {
            File(config.rootPath, path.replace("/", File.separator))
        }
    }

    private fun calculateInSampleSize(size: Int, reqSize: Int): Int {
        var inSampleSize = 1
        if (size > reqSize) {
            val halfSize = size / 2
            while (halfSize / inSampleSize >= reqSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override suspend fun uploadFile(
        id: FileObjectId,
        fileName: String,
        inputStream: InputStream,
    ): Unit = withContext(Dispatchers.IO) {
        val path = when (id) {
            is FileObjectId.Root -> ""
            is FileObjectId.Item -> id.id
        }
        val destinationDir = buildAbsoluteFile(path)

        require(destinationDir.exists() && destinationDir.isDirectory && destinationDir.canWrite()) {
            "Destination directory not found or cannot write: $path"
        }

        val destinationFile = File(destinationDir, fileName)

        inputStream.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    override suspend fun uploadFolder(
        id: FileObjectId,
        folderName: String,
        files: List<FileToUpload>,
    ): Unit = withContext(Dispatchers.IO) {
        val path = when (id) {
            is FileObjectId.Root -> ""
            is FileObjectId.Item -> id.id
        }
        val destinationDir = buildAbsoluteFile(path)

        require(destinationDir.exists() && destinationDir.isDirectory && destinationDir.canWrite()) {
            "Destination directory not found or cannot write: $path"
        }

        val folderDir = File(destinationDir, folderName)
        folderDir.mkdirs()

        files.forEach { fileToUpload ->
            val targetFile = File(folderDir, fileToUpload.relativePath.replace("/", File.separator))

            targetFile.parentFile?.mkdirs()

            fileToUpload.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    companion object
}
