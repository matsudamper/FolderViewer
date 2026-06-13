package net.matsudamper.folderviewer.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ClipboardRepository @Inject constructor() {
    enum class ClipboardMode { Copy, Cut }

    data class ClipboardState(
        val mode: ClipboardMode,
        val items: Set<FileItem>,
        val sourceDisplayPath: String,
    )

    private val _clipboardState = MutableStateFlow<ClipboardState?>(null)
    val clipboardState: StateFlow<ClipboardState?> = _clipboardState.asStateFlow()

    fun setClipboard(mode: ClipboardMode, items: Set<FileItem>, sourceDisplayPath: String) {
        _clipboardState.value = ClipboardState(mode, items, sourceDisplayPath)
    }

    fun clearClipboard() {
        _clipboardState.value = null
    }
}
