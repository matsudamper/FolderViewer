package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.matsudamper.folderviewer.common.FileObjectId

@Singleton
class ClipboardRepository @Inject constructor() {
    enum class ClipboardMode { Copy, Cut }

    data class ClipboardState(
        val mode: ClipboardMode,
        val fileIds: Set<FileObjectId.Item>,
    )

    private val _clipboardState = MutableStateFlow<ClipboardState?>(null)
    val clipboardState: StateFlow<ClipboardState?> = _clipboardState.asStateFlow()

    fun setClipboard(mode: ClipboardMode, fileIds: Set<FileObjectId.Item>) {
        _clipboardState.value = ClipboardState(mode, fileIds)
    }

    fun clearClipboard() {
        _clipboardState.value = null
    }
}
