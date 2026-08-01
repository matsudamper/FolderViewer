package net.matsudamper.folderviewer.repository

import android.net.Uri
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ShareUploadRepository @Inject constructor() {
    data class PendingFile(
        val uri: Uri,
        val fileName: String,
    )

    private val _pendingFiles = MutableStateFlow<List<PendingFile>?>(null)
    val pendingFiles: StateFlow<List<PendingFile>?> = _pendingFiles.asStateFlow()

    fun setPendingFiles(files: List<PendingFile>) {
        _pendingFiles.value = files.ifEmpty { null }
    }

    fun clear() {
        _pendingFiles.value = null
    }
}
