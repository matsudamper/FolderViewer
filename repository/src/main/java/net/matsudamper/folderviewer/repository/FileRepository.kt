package net.matsudamper.folderviewer.repository

import java.io.InputStream

interface FileRepository {
    suspend fun getFiles(path: String): List<FileItem>
    suspend fun getFileContent(path: String): InputStream
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
