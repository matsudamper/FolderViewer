package net.matsudamper.folderviewer.ui.permission

import androidx.compose.runtime.Immutable

data class PermissionRequestUiState(
    val grantedMessageVisible: Boolean,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onGrantPermission()
    }
}
