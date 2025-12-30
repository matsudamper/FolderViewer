package net.matsudamper.folderviewer.repository

import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare

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

    private suspend fun enumerateShares(session: Session): List<FileItem> = coroutineScope {
        CommonShares.map { shareName ->
            async {
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    // 短時間でタイムアウトするように設定したいが、smbj の API ではセッションレベルでの制御が主。
                    // 共有が存在しない場合、迅速にエラーを返すことを期待。
                    session.connectShare(shareName).use { share ->
                        if (share is DiskShare) {
                            FileItem(
                                name = shareName,
                                path = shareName,
                                isDirectory = true,
                                size = 0,
                                lastModified = 0,
                            )
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
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
        private val CommonShares = listOf(
            "Data", "Shared", "Public", "Storage", "homes", "users",
            "media", "video", "music", "photos", "backups", "share",
            "NAS", "Cloud", "External", "USB", "Transfer", "Temp",
            "common", "backup", "files", "documents", "work", "projects",
        )
    }
}
