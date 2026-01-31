package net.matsudamper.folderviewer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.runBlocking
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.RandomAccessFileRepository
import net.matsudamper.folderviewer.repository.RandomAccessSource
import net.matsudamper.folderviewer.repository.StorageRepository

class StreamingContentProvider : ContentProvider() {
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    private val storageRepository: StorageRepository by lazy {
        val appContext = requireContext().applicationContext
            ?: throw IllegalStateException("Context is not available")
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            StreamingContentProviderEntryPoint::class.java,
        )
        entryPoint.storageRepository()
    }

    override fun onCreate(): Boolean {
        handlerThread = HandlerThread("StreamingContentProvider").apply { start() }
        handler = Handler(handlerThread.looper)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 3) {
            return null
        }

        val fileName = pathSegments[2]
        val storageId = StorageId(pathSegments[0])
        val encodedFileId = pathSegments[1]
        val fileId = FileObjectId.Item(URLDecoder.decode(encodedFileId, "UTF-8"))

        val cols = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = MatrixCursor(cols)
        val row = cursor.newRow()

        val extension = fileName.substringAfterLast('.', "")
        val mimeType = if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }

        val fileSize = try {
            val size = runBlocking {
                storageRepository.getFileRepository(storageId)?.getFileSize(fileId)
            }

            size ?: 0L
        } catch (_: Exception) {
            0L
        }

        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(fileName)

                OpenableColumns.SIZE -> {
                    row.add(fileSize)
                }

                DocumentsContract.Document.COLUMN_MIME_TYPE -> row.add(mimeType)

                else -> row.add(null)
            }
        }

        return cursor
    }

    override fun getType(uri: Uri): String? {
        val fileName = uri.lastPathSegment ?: return null

        val extension = fileName.substringAfterLast('.', "")

        if (extension.isEmpty()) {
            return "application/octet-stream"
        }

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
        return mimeType
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw SecurityException("Only read mode is supported")
        }

        val pathSegments = uri.pathSegments

        require(pathSegments.size >= 3) { "Invalid URI (needs 3+ segments): $uri" }

        val storageId = StorageId(pathSegments[0])
        val encodedFileId = pathSegments[1]
        val fileId = FileObjectId.Item(URLDecoder.decode(encodedFileId, "UTF-8"))

        val source = runBlocking {
            val repository = storageRepository.getFileRepository(storageId)
            if (repository !is RandomAccessFileRepository) {
                throw IllegalStateException("storageId=$storageId is not RandomAccessFileRepository")
            }

            repository.openRandomAccess(fileId)
        }

        val storageManager = requireContext().getSystemService(StorageManager::class.java)
            ?: run {
                throw IllegalStateException("StorageManager not available")
            }

        val callback = RandomAccessProxyCallback(source)

        val pfd = storageManager.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            callback,
            handler,
        )

        return pfd
    }

    private class RandomAccessProxyCallback(
        private val source: RandomAccessSource,
    ) : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long {
            return source.size
        }

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            try {
                if (offset < 0) {
                    return -1
                }

                if (offset >= source.size) {
                    return 0
                }

                val bytesRead = source.readAt(offset, data, 0, size)

                return bytesRead
            } catch (_: Exception) {
                return -1
            }
        }

        override fun onRelease() {
            runCatching {
                source.close()
            }
        }

        override fun onFsync() = Unit
    }

    companion object {
        private const val AUTHORITY = "net.matsudamper.folderviewer.streaming"

        fun buildUri(storageId: StorageId, fileId: FileObjectId.Item, fileName: String): Uri {
            val encodedFileId = URLEncoder.encode(fileId.id, "UTF-8")
            val uri = Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(storageId.id)
                .appendPath(encodedFileId)
                .appendPath(fileName)
                .build()
            return uri
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StreamingContentProviderEntryPoint {
    fun storageRepository(): StorageRepository
}
