package net.matsudamper.folderviewer.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class SelectionModeRepository @Inject constructor() {
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    fun setSelectionMode(active: Boolean) {
        _isSelectionMode.value = active
    }
}
