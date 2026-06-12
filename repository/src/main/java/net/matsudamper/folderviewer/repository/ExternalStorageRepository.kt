package net.matsudamper.folderviewer.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import net.matsudamper.folderviewer.common.StorageId

@Singleton
class ExternalStorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val externalStorages: Flow<List<StorageConfiguration.External>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(loadExternalStorages())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val volumeCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            val callback = object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    trySend(loadExternalStorages())
                }
            }
            storageManager?.registerStorageVolumeCallback(context.mainExecutor, callback)
            callback
        } else {
            null
        }
        trySend(loadExternalStorages())
        awaitClose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && volumeCallback != null) {
                context.getSystemService(StorageManager::class.java)
                    ?.unregisterStorageVolumeCallback(volumeCallback)
            }
        }
    }.distinctUntilChanged()

    fun findExternalStorage(id: StorageId): StorageConfiguration.External? {
        return loadExternalStorages().find { it.id == id }
    }

    private fun loadExternalStorages(): List<StorageConfiguration.External> {
        val storageManager = context.getSystemService(StorageManager::class.java)
            ?: return emptyList()
        return storageManager.storageVolumes
            .mapNotNull { volume ->
                if (!volume.isRemovable) return@mapNotNull null
                if (volume.state != Environment.MEDIA_MOUNTED &&
                    volume.state != Environment.MEDIA_MOUNTED_READ_ONLY
                ) {
                    return@mapNotNull null
                }
                val uuid = volume.uuid ?: return@mapNotNull null
                val rootPath = getVolumeRootPath(storageManager, volume) ?: return@mapNotNull null
                StorageConfiguration.External(
                    id = StorageId("$ExternalStorageIdPrefix$uuid"),
                    name = volume.getDescription(context) ?: DefaultExternalStorageName,
                    rootPath = rootPath,
                )
            }
            .distinctBy { it.id }
    }

    private fun getVolumeRootPath(storageManager: StorageManager, volume: StorageVolume): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.absolutePath
        } else {
            context.getExternalFilesDirs(null)
                .filterNotNull()
                .firstOrNull { storageManager.getStorageVolume(it) == volume }
                ?.absolutePath
                ?.substringBefore("/Android/")
        }
    }

    companion object {
        private const val ExternalStorageIdPrefix = "external:"
        private const val DefaultExternalStorageName = "外部ストレージ"
    }
}
