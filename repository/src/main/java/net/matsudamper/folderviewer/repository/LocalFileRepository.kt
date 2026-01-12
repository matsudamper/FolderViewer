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

internal class LocalFileRepository(
    private val config: StorageConfiguration.Local,
) : FileRepository {
    override suspend fun getFiles(id: String?): List<FileItem> = withContext(Dispatchers.IO) {
        val path = id.orEmpty()
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
                id = relativePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                lastModified = file.lastModified(),
            )
        }?.sortedWith(
            compareBy<FileItem> { !it.isDirectory }
                .thenBy { it.displayPath.lowercase() },
        ).orEmpty()
    }

    override suspend fun getFileContent(path: String): InputStream = withContext(Dispatchers.IO) {
        val file = buildAbsoluteFile(path)

        require(file.exists() && file.canRead()) { "File not found or cannot read: $path" }

        FileInputStream(file)
    }

    override suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream = withContext(Dispatchers.IO) {
        try {
            val file = buildAbsoluteFile(path)

            if (!file.exists() || !file.canRead()) {
                return@withContext getFileContent(path)
            }

            FileInputStream(file).use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                stream.use { BitmapFactory.decodeStream(it, null, options) }

                val width = options.outWidth
                val height = options.outHeight

                if (width <= 0 || height <= 0) {
                    return@withContext getFileContent(path)
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(minOf(width, height), thumbnailSize)
                }

                FileInputStream(file).use { decodeStream ->
                    val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                        ?: return@withContext getFileContent(path)

                    val bos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    bitmap.recycle()

                    ByteArrayInputStream(bos.toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getFileContent(path)
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
        id: String?,
        fileName: String,
        inputStream: InputStream,
    ): Unit = withContext(Dispatchers.IO) {
        id ?: return@withContext
        val destinationDir = buildAbsoluteFile(id)

        require(destinationDir.exists() && destinationDir.isDirectory && destinationDir.canWrite()) {
            "Destination directory not found or cannot write: $id"
        }

        val destinationFile = File(destinationDir, fileName)

        inputStream.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    override suspend fun uploadFolder(
        id: String?,
        folderName: String,
        files: List<FileToUpload>,
    ): Unit = withContext(Dispatchers.IO) {
        id ?: return@withContext
        val destinationDir = buildAbsoluteFile(id)

        require(destinationDir.exists() && destinationDir.isDirectory && destinationDir.canWrite()) {
            "Destination directory not found or cannot write: $id"
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
