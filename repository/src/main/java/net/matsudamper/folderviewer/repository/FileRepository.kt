package net.matsudamper.folderviewer.repository

import java.io.InputStream
import net.matsudamper.folderviewer.common.FileObjectId

interface FileRepository {
    suspend fun getFiles(id: FileObjectId): List<FileItem>
    suspend fun getFileContent(fileId: FileObjectId.Item): InputStream
    suspend fun getThumbnail(fileId: FileObjectId.Item, thumbnailSize: Int): InputStream?
    suspend fun uploadFile(id: FileObjectId, fileName: String, inputStream: InputStream)
    suspend fun uploadFolder(id: FileObjectId, folderName: String, files: List<FileToUpload>)
}

data class FileToUpload(
    val relativePath: String,
    val inputStream: InputStream,
)

data class FileItem(
    val id: FileObjectId.Item,
    val displayPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
