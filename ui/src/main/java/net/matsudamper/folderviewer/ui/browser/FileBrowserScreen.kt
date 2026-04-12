package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    uiEvent: Flow<FileBrowserUiEvent>,
    onNavigateToUploadProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        uiState.callbacks.onBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val showCreateDirectoryDialog = remember { mutableStateOf(false) }
    val deleteConfirmCount = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiEvent) {
        uiEvent.collect { event ->
            when (event) {
                is FileBrowserUiEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.showAction) "表示" else null,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onNavigateToUploadProgress()
                    }
                }
                is FileBrowserUiEvent.ShowCreateDirectoryDialog -> {
                    showCreateDirectoryDialog.value = true
                }
                is FileBrowserUiEvent.ShowDeleteConfirmDialog -> {
                    deleteConfirmCount.value = event.count
                }
            }
        }
    }

    Box(modifier = modifier) {
        FileBrowserScreenContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            showCreateDirectoryDialog = showCreateDirectoryDialog.value,
            onCreateDirectoryDialogDismiss = { showCreateDirectoryDialog.value = false },
            onConfirmCreateDirectory = { directoryName ->
                uiState.callbacks.onConfirmCreateDirectory(directoryName)
                showCreateDirectoryDialog.value = false
            },
            deleteConfirmCount = deleteConfirmCount.value,
            onDeleteConfirmDialogDismiss = { deleteConfirmCount.value = null },
            onConfirmDelete = {
                uiState.callbacks.onConfirmDelete()
                deleteConfirmCount.value = null
            },
        )
    }
}
