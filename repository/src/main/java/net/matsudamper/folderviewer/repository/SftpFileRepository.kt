package net.matsudamper.folderviewer.repository

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
    private var session: Session? = null
    private var channelSftp: ChannelSftp? = null

    private suspend fun ensureConnected() = withContext(Dispatchers.IO) {
        if (session?.isConnected == true && channelSftp?.isConnected == true) {
            return@withContext
        }

        // セッションを作成して接続
        session = jsch.getSession(config.username, config.host, config.port).apply {
            setPassword(password)
            setConfig("StrictHostKeyChecking", "no") // 本番環境では適切なホストキー検証を実装すべき
            connect()
        }

        // SFTPチャネルを開く
        channelSftp = (session?.openChannel("sftp") as? ChannelSftp)?.apply {
            connect()
        }
    }

    override suspend fun getFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        ensureConnected()
        val channel = channelSftp ?: return@withContext emptyList()

        try {
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
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getFileContent(path: String): InputStream = withContext(Dispatchers.IO) {
        ensureConnected()
        val channel = channelSftp ?: throw IllegalStateException("SFTP channel not connected")
        channel.get(path)
    }

    fun disconnect() {
        channelSftp?.disconnect()
        session?.disconnect()
    }

    companion object {
        private const val MillisPerSecond = 1000L
    }
}
