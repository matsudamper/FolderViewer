package net.matsudamper.folderviewer.repository

import java.io.InputStream

interface FileRepository {
    suspend fun getFiles(path: String): List<FileItem>
    suspend fun getFileContent(path: String): InputStream
    suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream
    suspend fun uploadFile(destinationPath: String, fileName: String, inputStream: InputStream)
    suspend fun uploadFolder(destinationPath: String, folderName: String, files: List<FileToUpload>)
}

data class FileToUpload(
    val relativePath: String,
    val inputStream: InputStream,
)

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
