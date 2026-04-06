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
    )

    private val _clipboardState = MutableStateFlow<ClipboardState?>(null)
    val clipboardState: StateFlow<ClipboardState?> = _clipboardState.asStateFlow()

    fun setClipboard(mode: ClipboardMode, items: Set<FileItem>) {
        _clipboardState.value = ClipboardState(mode, items)
    }

    fun clearClipboard() {
        _clipboardState.value = null
    }
}
