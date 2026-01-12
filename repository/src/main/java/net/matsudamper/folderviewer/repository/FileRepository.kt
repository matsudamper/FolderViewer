package net.matsudamper.folderviewer.repository

import java.io.InputStream

interface FileRepository {
    suspend fun getFiles(id: String?): List<FileItem>
    suspend fun getFileContent(path: String): InputStream
    suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream?
    suspend fun uploadFile(id: String?, fileName: String, inputStream: InputStream)
    suspend fun uploadFolder(id: String?, folderName: String, files: List<FileToUpload>)
}

data class FileToUpload(
    val relativePath: String,
    val inputStream: InputStream,
)

data class FileItem(
    val displayName: String,
    val id: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
