package net.matsudamper.folderviewer.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories

class SmbFileRepository(
    private val config: StorageConfiguration.Smb,
) : FileRepository {
    private val client = SMBClient()

    override suspend fun getFiles(id: String?): List<FileItem> = withContext(Dispatchers.IO) {
        val path = id.orEmpty()
        client.connect(config.ip).use { connection ->
            val session = connection.authenticate(
                AuthenticationContext(
                    config.username,
                    config.password.toCharArray(),
                    null,
                ),
            )

            if (path.isEmpty()) {
                return@withContext enumerateShares(session)
            }

            listShareItems(session, path)
        }
    }

    override suspend fun getFileContent(path: String): InputStream = getFileContentInternal(path)

    override suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream = withContext(Dispatchers.IO) {
        val connection = client.connect(config.ip)
        try {
            val session = connection.authenticate(
                AuthenticationContext(
                    config.username,
                    config.password.toCharArray(),
                    null,
                ),
            )

            val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
            val shareName = parts[0]
            val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

            val share = session.connectShare(shareName) as? DiskShare
                ?: throw IllegalArgumentException("Share not found or not a DiskShare: $shareName")

            share.openFile(
                subPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { file ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                file.inputStream.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                val width = options.outWidth
                val height = options.outHeight

                if (width <= 0 || height <= 0) {
                    return@withContext getFileContentInternal(path, maxReadSize = MAX_THUMBNAIL_READ_SIZE.toLong())
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(minOf(width, height), thumbnailSize)
                }

                val bitmap = file.inputStream.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                } ?: return@withContext getFileContentInternal(path, maxReadSize = MAX_THUMBNAIL_READ_SIZE.toLong())

                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                bitmap.recycle()

                ByteArrayInputStream(bos.toByteArray())
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            getFileContentInternal(path, maxReadSize = MAX_THUMBNAIL_READ_SIZE.toLong())
        } finally {
            connection.close()
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

    private suspend fun getFileContentInternal(
        path: String,
        maxReadSize: Long? = null,
    ): InputStream = withContext(Dispatchers.IO) {
        val connection = client.connect(config.ip)
        try {
            val session = connection.authenticate(
                AuthenticationContext(
                    config.username,
                    config.password.toCharArray(),
                    null,
                ),
            )

            val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
            val shareName = parts[0]
            val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

            val share = session.connectShare(shareName) as? DiskShare
                ?: throw IllegalArgumentException("Share not found or not a DiskShare: $shareName")

            val file = share.openFile(
                subPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )

            val fileSize = file.fileInformation.standardInformation.endOfFile
            val smbStream = file.inputStream

            // Return a wrapper stream that closes everything
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
                        try {
                            file.close()
                        } finally {
                            try {
                                share.close()
                            } finally {
                                try {
                                    session.close()
                                } finally {
                                    connection.close()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            connection.close() // Close if setup failed
            throw e
        }
    }

    private fun enumerateShares(session: Session): List<FileItem> {
        return try {
            val transport = SMBTransportFactories.SRVSVC.getTransport(session)
            val serverService = ServerService(transport)
            val shares = serverService.shares1

            shares
                .filter { it.type == 0 } // STYPE_DISKTREE
                .map {
                    FileItem(
                        displayPath = it.netName,
                        id = it.netName,
                        isDirectory = true,
                        size = 0,
                        lastModified = 0,
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun listShareItems(session: Session, path: String): List<FileItem> {
        val parts = path.split("/", limit = PATH_SPLIT_LIMIT)
        val shareName = parts[0]
        val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

        return session.connectShare(shareName).use { share ->
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
                    id = "$shareName/$displaySubPath${info.fileName}",
                    isDirectory = isDirectory,
                    size = info.endOfFile,
                    lastModified = info.changeTime.toEpochMillis(),
                )
            }
    }

    override suspend fun uploadFile(
        id: String?,
        fileName: String,
        inputStream: InputStream,
    ) {
        id ?: return
        withContext(Dispatchers.IO) {
            client.connect(config.ip).use { connection ->
                val session = connection.authenticate(
                    AuthenticationContext(
                        config.username,
                        config.password.toCharArray(),
                        null,
                    ),
                )

                val parts = id.split("/", limit = PATH_SPLIT_LIMIT)
                val shareName = parts[0]
                val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

                val share = session.connectShare(shareName) as? DiskShare
                    ?: throw IllegalArgumentException("Share not found or not a DiskShare: $shareName")

                share.use { diskShare ->
                    val fullPath = if (subPath.isEmpty()) fileName else "$subPath\\$fileName"

                    diskShare.openFile(
                        fullPath,
                        EnumSet.of(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OVERWRITE_IF,
                        null,
                    ).use { file ->
                        file.outputStream.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
        }
    }

    override suspend fun uploadFolder(
        id: String?,
        folderName: String,
        files: List<FileToUpload>,
    ) {
        id ?: return
        withContext(Dispatchers.IO) {
            client.connect(config.ip).use { connection ->
                val session = connection.authenticate(
                    AuthenticationContext(
                        config.username,
                        config.password.toCharArray(),
                        null,
                    ),
                )

                val parts = id.split("/", limit = PATH_SPLIT_LIMIT)
                val shareName = parts[0]
                val subPath = parts.getOrNull(1)?.replace("/", "\\").orEmpty()

                val share = session.connectShare(shareName) as? DiskShare
                    ?: throw IllegalArgumentException("Share not found or not a DiskShare: $shareName")

                share.use { diskShare ->
                    val basePath = if (subPath.isEmpty()) folderName else "$subPath\\$folderName"

                    diskShare.mkdir(basePath)

                    files.forEach { fileToUpload ->
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
                                fileToUpload.inputStream.copyTo(outputStream)
                            }
                        }
                    }
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

    companion object {
        private const val PATH_SPLIT_LIMIT = 2
        private const val MAX_THUMBNAIL_READ_SIZE = 1024 * 1024 // 1MB
    }
}
