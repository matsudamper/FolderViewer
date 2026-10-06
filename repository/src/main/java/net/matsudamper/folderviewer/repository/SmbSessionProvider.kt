package net.matsudamper.folderviewer.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import net.matsudamper.folderviewer.common.StorageId

/**
 * 認証済みの SMB セッションを接続先ごとに1つだけ保持して使い回す。
 * 操作ごとにセッションを張るとサーバのセッション上限に達して
 * STATUS_REQUEST_NOT_ACCEPTED になるため、セッション・共有は閉じずに共有する。
 * セッションが壊れたと判断できるエラーでは接続を破棄して張り直す。
 */
internal class SmbSessionProvider private constructor(
    private val key: Key,
) {
    val thumbnailSemaphore = Semaphore(MAX_CONCURRENT_THUMBNAIL_LOADS)

    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(120, TimeUnit.SECONDS)
            .withSoTimeout(120, TimeUnit.SECONDS)
            .withReadBufferSize(1024 * 1024)
            .withWriteBufferSize(1024 * 1024)
            .withMultiProtocolNegotiate(true)
            .build(),
    )
    private val sessionMutex = Mutex()
    private var cachedSession: Session? = null

    suspend fun <T> withSession(block: suspend (Session) -> T): T {
        return withRecovery(retryBody = true) { session -> block(session) }
    }

    suspend fun <T> withDiskShare(shareName: String, block: suspend (DiskShare) -> T): T {
        return withRecovery(retryBody = true) { session -> block(connectDiskShare(session, shareName)) }
    }

    suspend fun <T> withDiskShareNoRetry(shareName: String, block: suspend (DiskShare) -> T): T {
        return withRecovery(retryBody = false) { session -> block(connectDiskShare(session, shareName)) }
    }

    private suspend fun <T> withRecovery(retryBody: Boolean, block: suspend (Session) -> T): T {
        val firstSession = acquireSessionWithRetry()
        return try {
            block(firstSession)
        } catch (e: Exception) {
            if (!isSessionBroken(e)) throw e
            invalidate(firstSession)
            if (!retryBody) throw e
            runInvalidatingOnBroken(acquireSessionWithRetry(), block)
        }
    }

    private suspend fun <T> runInvalidatingOnBroken(session: Session, block: suspend (Session) -> T): T {
        return try {
            block(session)
        } catch (e: Exception) {
            if (isSessionBroken(e)) invalidate(session)
            throw e
        }
    }

    private suspend fun acquireSessionWithRetry(): Session {
        return try {
            acquireSession()
        } catch (e: Exception) {
            if (!isSessionBroken(e)) throw e
            delay(RECOVERY_DELAY_MILLIS)
            acquireSession()
        }
    }

    private suspend fun acquireSession(): Session = sessionMutex.withLock {
        val current = cachedSession
        if (current != null && current.connection.isConnected) {
            return@withLock current
        }
        val connection = client.connect(key.ip)
        val session = try {
            connection.authenticate(
                AuthenticationContext(key.username, key.password.toCharArray(), null),
            )
        } catch (e: Exception) {
            runCatching { connection.close(true) }
            throw e
        }
        cachedSession = session
        session
    }

    private suspend fun invalidate(brokenSession: Session) {
        sessionMutex.withLock {
            if (cachedSession !== brokenSession) return@withLock
            cachedSession = null
            runCatching { brokenSession.connection.close(true) }
        }
    }

    private fun connectDiskShare(session: Session, shareName: String): DiskShare {
        return session.connectShare(shareName) as? DiskShare
            ?: throw IllegalArgumentException("Share not found or not a DiskShare: $shareName")
    }

    private fun isSessionBroken(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause }.take(MAX_CAUSE_DEPTH).any { cause ->
            when (cause) {
                is TransportException -> true
                is SMBApiException -> cause.status in sessionBrokenStatuses
                else -> false
            }
        }
    }

    private fun disconnect() {
        cachedSession?.let { session -> runCatching { session.connection.close(true) } }
        cachedSession = null
    }

    private data class Key(
        val ip: String,
        val username: String,
        val password: String,
    )

    companion object {
        private const val MAX_CONCURRENT_THUMBNAIL_LOADS = 3
        private const val RECOVERY_DELAY_MILLIS = 1000L
        private const val MAX_CAUSE_DEPTH = 10

        private val sessionBrokenStatuses = setOf(
            NtStatus.STATUS_REQUEST_NOT_ACCEPTED,
            NtStatus.STATUS_NETWORK_SESSION_EXPIRED,
            NtStatus.STATUS_USER_SESSION_DELETED,
            NtStatus.STATUS_NETWORK_NAME_DELETED,
            NtStatus.STATUS_CONNECTION_DISCONNECTED,
            NtStatus.STATUS_CONNECTION_RESET,
            NtStatus.STATUS_INSUFF_SERVER_RESOURCES,
        )

        private val providers = ConcurrentHashMap<StorageId, SmbSessionProvider>()

        fun get(config: StorageConfiguration.Smb): SmbSessionProvider {
            val key = Key(ip = config.ip, username = config.username, password = config.password)
            return providers.compute(config.id) { _, existing ->
                if (existing != null && existing.key == key) {
                    existing
                } else {
                    existing?.disconnect()
                    SmbSessionProvider(key)
                }
            } ?: throw IllegalStateException("SmbSessionProvider was not created: ${config.id}")
        }

        fun release(storageId: StorageId) {
            providers.remove(storageId)?.disconnect()
        }
    }
}
