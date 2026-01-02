package net.matsudamper.folderviewer.repository

import java.io.InputStream

interface FileRepository {
    suspend fun getFiles(path: String): List<FileItem>
    suspend fun getFileContent(path: String): InputStream
    suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
