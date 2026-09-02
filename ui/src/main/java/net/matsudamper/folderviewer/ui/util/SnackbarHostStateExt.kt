package net.matsudamper.folderviewer.ui.util

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

suspend fun SnackbarHostState.showDismissibleSnackbar(
    message: String,
    actionLabel: String? = null,
): SnackbarResult {
    return showSnackbar(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = true,
        duration = if (actionLabel == null) {
            SnackbarDuration.Short
        } else {
            SnackbarDuration.Long
        },
    )
}
