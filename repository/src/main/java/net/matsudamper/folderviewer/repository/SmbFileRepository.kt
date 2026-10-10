package net.matsudamper.folderviewer.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.PipeShare
import com.rapid7.client.dcerpc.Interface
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransport
import com.rapid7.helper.smbj.io.SMB2Exception
import com.rapid7.helper.smbj.share.NamedPipe
import net.matsudamper.folderviewer.common.FileObjectId

class SmbFileRepository(
    private val config: StorageConfiguration.Smb,
) : RandomAccessFileRepository {
    private val sessionProvider = SmbSessionProvider.get(config)

    override suspend fun getFiles(id: FileObjectId): List<FileItem> = withContext(Dispatchers.IO) {
        val path = when (id) {
            is FileObjectId.Root -> ""
            is FileObjectId.Item -> id.id
        }
        if (path.isEmpty()) {
            try {
                sessionProvider.withSession { session -> enumerateShares(session) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                listOf()
            }
        } else {
            listShareItems(path)
        }
    }

    override suspend fun getFileContent(fileId: FileObjectId.Item): InputStream = getFileContentInternal(fileId.id)

    override suspend fun getFileSize(fileId: FileObjectId.Item): Long = withContext(Dispatchers.IO) {
        val parts = fileId.id.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()
        require(subPath.isNotEmpty()) { "Cannot get size of share root: $shareName" }

        sessionProvider.withDiskShare(shareName) { diskShare ->
            diskShare.openFile(
                subPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { file ->
                file.fileInformation.standardInformation.endOfFile
            }
        }
    }

    override suspend fun getFileInfo(fileId: FileObjectId.Item): FileItem = withContext(Dispatchers.IO) {
        val fileName = fileId.id.substringAfterLast("/")
        val parts = fileId.id.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()
        require(subPath.isNotEmpty()) { "Cannot get info of share root: $shareName" }

        sessionProvider.withDiskShare(shareName) { diskShare ->
            val isDirectory = diskShare.folderExists(subPath)
            val size: Long
            val lastModified: Long
            if (isDirectory) {
                size = 0L
                lastModified = diskShare.getFileInformation(subPath).basicInformation.changeTime.toEpochMillis()
            } else {
                val fileInfo = diskShare.openFile(
                    subPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                ).use { it.fileInformation }
                size = fileInfo.standardInformation.endOfFile
                lastModified = fileInfo.basicInformation.changeTime.toEpochMillis()
            }
            FileItem(
                id = fileId,
                displayPath = fileName,
                isDirectory = isDirectory,
                size = size,
                lastModified = lastModified,
            )
        }
    }

    override suspend fun deleteFile(fileId: FileObjectId.Item): Unit = withContext(Dispatchers.IO) {
        val parts = fileId.id.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()
        require(subPath.isNotEmpty()) { "Cannot delete share root: $shareName" }
        sessionProvider.withDiskShareNoRetry(shareName) { diskShare ->
            try {
                diskShare.rm(subPath)
            } catch (e: SMBApiException) {
                if (e.statusCode == NtStatus.STATUS_CANNOT_DELETE.value) {
                    clearReadOnlyAttribute(diskShare, subPath)
                    diskShare.rm(subPath)
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun deleteDirectory(dirId: FileObjectId.Item): Unit = withContext(Dispatchers.IO) {
        val parts = dirId.id.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()
        require(subPath.isNotEmpty()) { "Cannot delete share root: $shareName" }
        sessionProvider.withDiskShareNoRetry(shareName) { diskShare ->
            try {
                diskShare.rmdir(subPath, false)
            } catch (e: SMBApiException) {
                if (e.statusCode == NtStatus.STATUS_CANNOT_DELETE.value) {
                    clearReadOnlyAttribute(diskShare, subPath)
                    diskShare.rmdir(subPath, false)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * 読み取り専用属性が付与されたファイル/ディレクトリは削除時に STATUS_CANNOT_DELETE を返すため、
     * FILE_ATTRIBUTE_NORMAL を設定して属性をクリアする。
     */
    private fun clearReadOnlyAttribute(share: DiskShare, path: String) {
        val basicInfo = FileBasicInformation(
            FileBasicInformation.DONT_SET,
            FileBasicInformation.DONT_SET,
            FileBasicInformation.DONT_SET,
            FileBasicInformation.DONT_SET,
            FileAttributes.FILE_ATTRIBUTE_NORMAL.value,
        )
        share.setFileInformation(path, basicInfo)
    }

    override suspend fun getThumbnail(fileId: FileObjectId.Item, thumbnailSize: Int): InputStream = sessionProvider.thumbnailSemaphore.withPermit {
        withContext(Dispatchers.IO) {
            try {
                val parts = fileId.id.split("/", limit = PATH_SPLIT_LIMIT)
                val shareName = parts[0]
                val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

                sessionProvider.withDiskShare(shareName) { diskShare ->
                    diskShare.openFile(
                        subPath,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null,
                    ).use { file ->
                        createThumbnailStream(file, thumbnailSize)
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                throw IOException("Failed to load thumbnail: ${fileId.id}", e)
            }
        }
    }

    /**
     * 1つのストリームから mark/reset を使ってサイズ判定と本デコードを行い、
     * SMBリソースをこの関数内で完結させた縮小画像のメモリ上ストリームを返す。
     * デコードできない場合は読み込み済みの先頭バイト列を返す。
     */
    private fun createThumbnailStream(file: com.hierynomus.smbj.share.File, thumbnailSize: Int): InputStream {
        java.io.BufferedInputStream(file.inputStream, DECODE_BUFFER_SIZE).use { input ->
            input.mark(MAX_THUMBNAIL_READ_SIZE)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)

            val width = options.outWidth
            val height = options.outHeight

            input.reset()

            if (width <= 0 || height <= 0) {
                return ByteArrayInputStream(readHeadBytes(input))
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(minOf(width, height), thumbnailSize)
            }

            val bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
                ?: throw IOException("Failed to decode thumbnail")

            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            bitmap.recycle()

            return ByteArrayInputStream(bos.toByteArray())
        }
    }

    private fun readHeadBytes(input: InputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(DECODE_BUFFER_SIZE)
        var total = 0
        while (total < MAX_THUMBNAIL_READ_SIZE) {
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_THUMBNAIL_READ_SIZE - total))
            if (read == -1) break
            bos.write(buffer, 0, read)
            total += read
        }
        return bos.toByteArray()
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

    private suspend fun getFileContentInternal(
        path: String,
        maxReadSize: Long? = null,
    ): InputStream = withContext(Dispatchers.IO) {
        val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

        val file = sessionProvider.withDiskShare(shareName) { share ->
            share.openFile(
                subPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
        }
        try {
            val fileSize = file.fileInformation.standardInformation.endOfFile
            val smbStream = file.inputStream

            object : InputStream() {
                private var bytesRead: Long = 0
                private val expectedSize = maxReadSize?.let { minOf(it, fileSize) } ?: fileSize

                override fun read(): Int {
                    if (maxReadSize != null && bytesRead >= maxReadSize) return -1
                    val result = smbStream.read()
                    if (result == -1 && bytesRead < expectedSize) {
                        throw IOException("Premature EOF: read $bytesRead bytes, expected $expectedSize")
                    }
                    if (result != -1) bytesRead++
                    return result
                }

                override fun read(b: ByteArray): Int {
                    return read(b, 0, b.size)
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (maxReadSize != null && bytesRead >= maxReadSize) return -1
                    val remaining = maxReadSize?.let { it - bytesRead } ?: Long.MAX_VALUE
                    val toRead = if (len > remaining) remaining.toInt() else len
                    val result = smbStream.read(b, off, toRead)

                    if (result == -1 && bytesRead < expectedSize) {
                        throw IOException("Premature EOF: read $bytesRead bytes, expected $expectedSize")
                    }

                    if (result != -1) bytesRead += result
                    return result
                }

                override fun skip(n: Long): Long {
                    val remaining = maxReadSize?.let { it - bytesRead } ?: Long.MAX_VALUE
                    val toSkip = if (n > remaining) remaining else n
                    val result = smbStream.skip(toSkip)
                    bytesRead += result
                    return result
                }

                override fun available(): Int {
                    val available = smbStream.available()
                    val remaining = maxReadSize?.let { (it - bytesRead).toInt() } ?: Int.MAX_VALUE
                    return if (available > remaining) remaining else available
                }

                override fun close() {
                    try {
                        smbStream.close()
                    } finally {
                        file.close()
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { file.close() }
            throw e
        }
    }

    private suspend fun enumerateShares(session: Session): List<FileItem> {
        val pipeShare = sessionProvider.connectShare(session, IPC_SHARE_NAME) as? PipeShare
            ?: throw IOException("$IPC_SHARE_NAME is not a PipeShare")
        val shares = openSrvsvcPipe(session, pipeShare).use { namedPipe ->
            val transport = SMBTransport(namedPipe)
            transport.bind(Interface.SRVSVC_V3_0, Interface.NDR_32BIT_V2)
            ServerService(transport).shares1
        }

        return shares
            .filter { it.type == 0 } // STYPE_DISKTREE
            .map {
                FileItem(
                    displayPath = it.netName,
                    id = FileObjectId.Item(storageId = config.id, id = it.netName),
                    isDirectory = true,
                    size = 0,
                    lastModified = 0,
                )
            }
    }

    private suspend fun openSrvsvcPipe(session: Session, pipeShare: PipeShare): NamedPipe {
        return try {
            NamedPipe(session, pipeShare, SRVSVC_PIPE_NAME)
        } catch (e: SMB2Exception) {
            if (e.status != NtStatus.STATUS_PIPE_NOT_AVAILABLE) throw e
            delay(PIPE_NOT_AVAILABLE_RETRY_DELAY_MILLIS)
            NamedPipe(session, pipeShare, SRVSVC_PIPE_NAME)
        }
    }

    private suspend fun listShareItems(path: String): List<FileItem> {
        val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

        return sessionProvider.withSession { session ->
            val share = sessionProvider.connectShare(session, shareName)
            if (share is DiskShare) {
                listItems(share, shareName, subPath)
            } else {
                emptyList()
            }
        }
    }

    private fun listItems(share: DiskShare, shareName: String, subPath: String): List<FileItem> {
        return share.list(subPath)
            .filter { it.fileName != "." && it.fileName != ".." }
            .map { info ->
                val isDirectory = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                val displaySubPath = if (subPath.isEmpty()) "" else "${subPath.replace("\\", "/")}/"
                FileItem(
                    displayPath = info.fileName,
                    id = FileObjectId.Item(storageId = config.id, id = "$shareName/$displaySubPath${info.fileName}"),
                    isDirectory = isDirectory,
                    size = info.endOfFile,
                    lastModified = info.changeTime.toEpochMillis(),
                )
            }
    }

    override suspend fun uploadFile(
        id: FileObjectId,
        fileName: String,
        inputStream: InputStream,
        size: Long,
        onRead: FlowCollector<Long>,
        overwrite: Boolean,
    ) {
        val path = when (id) {
            is FileObjectId.Root -> return
            is FileObjectId.Item -> id.id
        }
        withContext(Dispatchers.IO) {
            val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
            val shareName = parts[0]
            val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

            sessionProvider.withDiskShareNoRetry(shareName) { diskShare ->
                val fullPath = if (subPath.isEmpty()) fileName else "$subPath\\$fileName"

                diskShare.openFile(
                    fullPath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    null,
                    SMB2ShareAccess.ALL,
                    if (overwrite) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_CREATE,
                    null,
                ).use { file ->
                    file.outputStream.use { outputStream ->
                        coroutineScope {
                            val progressInputStream = ProgressInputStream(inputStream)
                            val job = launch {
                                progressInputStream.onRead.collect(onRead)
                            }

                            progressInputStream.copyTo(outputStream)
                            job.cancel()
                        }
                    }
                }
            }
        }
    }

    override suspend fun uploadFolder(
        id: FileObjectId,
        folderName: String,
        files: List<FileToUpload>,
        onRead: FlowCollector<UploadProgress>,
    ) {
        val path = when (id) {
            is FileObjectId.Root -> return
            is FileObjectId.Item -> id.id
        }
        withContext(Dispatchers.IO) {
            uploadFolderInternal(path = path, folderName = folderName, files = files, onRead = onRead)
        }
    }

    private suspend fun uploadFolderInternal(
        path: String,
        folderName: String,
        files: List<FileToUpload>,
        onRead: FlowCollector<UploadProgress>,
    ) {
        val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

        sessionProvider.withDiskShareNoRetry(shareName) { diskShare ->
            val basePath = if (subPath.isEmpty()) folderName else "$subPath\\$folderName"

            diskShare.mkdir(basePath)

            var uploadedSize = 0L
            var completedFiles = 0
            files.forEach { fileToUpload ->
                var lastReadSize = 0L
                uploadFolderEntry(diskShare, basePath, fileToUpload) { fileReadSize ->
                    lastReadSize = fileReadSize
                    onRead.emit(UploadProgress(uploadedSize + fileReadSize, completedFiles))
                }
                uploadedSize += fileToUpload.size ?: lastReadSize
                completedFiles++
            }
        }
    }

    private suspend fun uploadFolderEntry(
        diskShare: DiskShare,
        basePath: String,
        fileToUpload: FileToUpload,
        onRead: suspend (Long) -> Unit,
    ) {
        val fullPath = "$basePath\\${fileToUpload.relativePath.replace("/", "\\")}"

        val parentPath = fullPath.substringBeforeLast("\\", "")
        if (parentPath.isNotEmpty() && parentPath != basePath) {
            createDirectoryRecursively(diskShare, parentPath)
        }

        diskShare.openFile(
            fullPath,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null,
        ).use { file ->
            file.outputStream.use { outputStream ->
                coroutineScope {
                    val progressInputStream = ProgressInputStream(fileToUpload.inputStream)
                    val job = launch {
                        progressInputStream.onRead.collect { fileReadSize ->
                            onRead(fileReadSize)
                        }
                    }

                    progressInputStream.use { input ->
                        input.copyTo(outputStream)
                    }
                    job.cancel()
                }
            }
        }
    }

    private fun createDirectoryRecursively(share: DiskShare, path: String) {
        val parts = path.split("\\")
        var currentPath = ""

        parts.forEach { part ->
            currentPath = if (currentPath.isEmpty()) part else "$currentPath\\$part"
            runCatching {
                share.mkdir(currentPath)
            }
        }
    }

    override suspend fun getViewSourceUri(fileId: FileObjectId.Item): ViewSourceUri {
        return ViewSourceUri.StreamProvider(fileId)
    }

    override suspend fun createDirectory(
        id: FileObjectId,
        directoryName: String,
    ): FileObjectId.Item {
        val path = when (id) {
            is FileObjectId.Root -> throw UnsupportedOperationException("Cannot create directory in root")
            is FileObjectId.Item -> id.id
        }
        return withContext(Dispatchers.IO) {
            val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
            val shareName = parts[0]
            val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

            sessionProvider.withDiskShare(shareName) { diskShare ->
                val fullPath = if (subPath.isEmpty()) directoryName else "$subPath\\$directoryName"
                when {
                    diskShare.folderExists(fullPath) -> Unit
                    diskShare.fileExists(fullPath) -> throw IllegalStateException("同名のファイルが既に存在します: $fullPath")
                    else -> diskShare.mkdir(fullPath)
                }
            }
            FileObjectId.Item(config.id, "$path/$directoryName")
        }
    }

    override suspend fun openRandomAccess(fileId: FileObjectId.Item): RandomAccessSource {
        return withContext(Dispatchers.IO) {
            val parts = fileId.id.split("/", limit = PATH_SPLIT_LIMIT)
            val shareName = parts[0]
            val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

            sessionProvider.withDiskShare(shareName) { share ->
                val file = share.openFile(
                    subPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
                val fileSize = try {
                    file.fileInformation.standardInformation.endOfFile
                } catch (e: Exception) {
                    runCatching { file.close() }
                    throw e
                }

                RandomAccessSourceImpl(
                    file = file,
                    fileSize = fileSize,
                )
            }
        }
    }

    private class RandomAccessSourceImpl(
        private val file: com.hierynomus.smbj.share.File,
        fileSize: Long,
    ) : RandomAccessSource {
        override val size: Long = fileSize
        private var closed = false

        override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
            return try {
                if (offset >= size) {
                    return 0
                }

                val maxLength = (size - offset).coerceAtMost(length.toLong()).toInt()
                if (maxLength <= 0) {
                    return 0
                }

                val bytesRead = file.read(buffer, offset, bufferOffset, maxLength)

                when {
                    bytesRead < 0 -> -1
                    bytesRead == 0 && offset < size -> 0
                    else -> bytesRead
                }
            } catch (_: Exception) {
                -1
            }
        }

        override fun close() {
            if (closed) return
            closed = true

            runCatching { file.close() }
        }
    }

    companion object {
        private const val PATH_SPLIT_LIMIT = 2
        private const val IPC_SHARE_NAME = "IPC$"
        private const val SRVSVC_PIPE_NAME = "srvsvc"
        private const val PIPE_NOT_AVAILABLE_RETRY_DELAY_MILLIS = 3000L
        private const val MAX_THUMBNAIL_READ_SIZE = 1024 * 1024 // 1MB
        private const val DECODE_BUFFER_SIZE = 16 * 1024
    }
}
