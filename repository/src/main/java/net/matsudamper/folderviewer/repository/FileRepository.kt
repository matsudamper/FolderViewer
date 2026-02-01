package net.matsudamper.folderviewer.repository

import java.io.Closeable
import java.io.InputStream
import kotlinx.coroutines.flow.FlowCollector
import net.matsudamper.folderviewer.common.FileObjectId

interface FileRepository {
    suspend fun getFiles(id: FileObjectId): List<FileItem>
    suspend fun getFileContent(fileId: FileObjectId.Item): InputStream
    suspend fun getFileSize(fileId: FileObjectId.Item): Long
    suspend fun getThumbnail(fileId: FileObjectId.Item, thumbnailSize: Int): InputStream?

    suspend fun uploadFile(
        id: FileObjectId,
        fileName: String,
        inputStream: InputStream,
        onRead: FlowCollector<Long>,
    )

    suspend fun uploadFolder(
        id: FileObjectId,
        folderName: String,
        files: List<FileToUpload>,
        onRead: FlowCollector<UploadProgress>,
    )

    suspend fun getViewSourceUri(fileId: FileObjectId.Item): ViewSourceUri

    suspend fun createDirectory(
        id: FileObjectId,
        directoryName: String,
    )
}

interface RandomAccessFileRepository : FileRepository {
    suspend fun openRandomAccess(fileId: FileObjectId.Item): RandomAccessSource
}

interface RandomAccessSource : Closeable {
    /**
     * (Bytes)
     */
    val size: Long
    fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int
}

sealed interface ViewSourceUri {
    /**
     * 端末内のファイル
     */
    data class LocalFile(val path: String) : ViewSourceUri

    /**
     * 認証付きURLが発行される場合
     * Sharepoint等
     */
    data class RemoteUrl(val url: String) : ViewSourceUri

    /**
     * Streamを中継する必要がある場合
     * SMB等
     */
    data class StreamProvider(val fileId: FileObjectId.Item) : ViewSourceUri
}

/**
 * @property uploadedBytes アップロード済みバイト数
 * @property completedFiles アップロード完了ファイル数
 */
data class UploadProgress(
    val uploadedBytes: Long,
    val completedFiles: Int,
)

/**
 * @property size (Bytes)
 */
data class FileToUpload(
    val relativePath: String,
    val inputStream: InputStream,
    val size: Long?,
)

/**
 * @property size (Bytes)
 */
data class FileItem(
    val id: FileObjectId.Item,
    val displayPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
