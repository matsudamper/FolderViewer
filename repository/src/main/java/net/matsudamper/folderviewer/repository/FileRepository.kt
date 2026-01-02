package net.matsudamper.folderviewer.repository

import java.io.InputStream

interface FileRepository {
    suspend fun getFiles(path: String): FileRepositoryResult<List<FileItem>>
    suspend fun getFileContent(path: String): FileRepositoryResult<InputStream>
}

sealed interface FileRepositoryResult<out T> {
    data class Success<T>(val value: T) : FileRepositoryResult<T>
    data class Error(val throwable: Throwable) : FileRepositoryResult<Nothing>
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
