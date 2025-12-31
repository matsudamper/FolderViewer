package net.matsudamper.folderviewer.repository

import android.util.Log
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.util.Vector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SftpFileRepository(
    private val config: StorageConfiguration.Sftp,
    private val password: String,
) : FileRepository {
    private val jsch = JSch()

    private suspend fun createConnection(): Pair<Session, ChannelSftp> = withContext(Dispatchers.IO) {
        // セッションを作成して接続
        val session = jsch.getSession(config.username, config.host, config.port).apply {
            setPassword(password)
            // TODO: 本番環境では適切なホストキー検証を実装すべき
            // known_hostsファイルを使用するか、ホストキーを保存・検証するメカニズムを追加する
            setConfig("StrictHostKeyChecking", "no")
            connect()
        }

        // SFTPチャネルを開く
        val channelSftp = (session.openChannel("sftp") as? ChannelSftp)?.apply {
            connect()
        } ?: throw IllegalStateException("Failed to open SFTP channel")

        Pair(session, channelSftp)
    }

    override suspend fun getFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            val (s, c) = createConnection()
            session = s
            channel = c

            val normalizedPath = if (path.isEmpty()) "." else path
            @Suppress("UNCHECKED_CAST")
            val entries = channel.ls(normalizedPath) as? Vector<ChannelSftp.LsEntry>
                ?: return@withContext emptyList()

            entries
                .filter { it.filename != "." && it.filename != ".." }
                .map { entry ->
                    val attrs = entry.attrs
                    val fullPath = if (path.isEmpty()) {
                        entry.filename
                    } else {
                        "$path/${entry.filename}"
                    }

                    FileItem(
                        name = entry.filename,
                        path = fullPath,
                        isDirectory = attrs.isDir,
                        size = attrs.size,
                        lastModified = attrs.mTime.toLong() * MillisPerSecond,
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files at path: $path for ${config.host}:${config.port}", e)
            emptyList()
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    override suspend fun getFileContent(path: String): InputStream = withContext(Dispatchers.IO) {
        val (session, channel) = try {
            createConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to SFTP server ${config.host}:${config.port}", e)
            throw IllegalStateException(
                "Failed to connect to SFTP server ${config.host}:${config.port}. " +
                    "Please check your credentials and network connection.",
                e,
            )
        }

        try {
            channel.get(path)
        } catch (e: Exception) {
            channel.disconnect()
            session.disconnect()
            Log.e(TAG, "Failed to retrieve file at path: $path from ${config.host}:${config.port}", e)
            throw IllegalStateException(
                "Failed to retrieve file at path: $path. The file may not exist or you may not have permission to access it.",
                e,
            )
        }
    }

    companion object {
        private const val TAG = "SftpFileRepository"
        private const val MillisPerSecond = 1000L
    }
}
