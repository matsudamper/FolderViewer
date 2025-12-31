package net.matsudamper.folderviewer.repository

import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories

class SmbFileRepository(
    private val config: StorageConfiguration.Smb,
    private val password: String,
) : FileRepository {
    private val client = SMBClient()

    override suspend fun getFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        client.connect(config.ip).use { connection ->
            val session = connection.authenticate(
                AuthenticationContext(
                    config.username,
                    password.toCharArray(),
                    null,
                ),
            )

            if (path.isEmpty()) {
                return@withContext enumerateShares(session)
            }

            listShareItems(session, path)
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
                        name = it.netName,
                        path = it.netName,
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
        val parts = path.split("/", limit = PathSplitLimit)
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
                    name = info.fileName,
                    path = "$shareName/$displaySubPath${info.fileName}",
                    isDirectory = isDirectory,
                    size = info.endOfFile,
                    lastModified = info.changeTime.toEpochMillis(),
                )
            }
    }

    override suspend fun getFileContent(path: String): InputStream {
        throw UnsupportedOperationException("File content retrieval is not yet implemented.")
    }

    companion object {
        private const val PathSplitLimit = 2
    }
}
