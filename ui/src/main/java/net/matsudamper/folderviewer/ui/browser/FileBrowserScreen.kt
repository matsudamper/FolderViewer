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
import net.matsudamper.folderviewer.ui.util.showDismissibleSnackbar

@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    uiEvent: Flow<FileBrowserUiEvent>,
    onNavigateToUploadProgress: () -> Unit,
    onOpenExtractResult: (Long) -> Unit,
    onNavigateToExtractDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        uiState.callbacks.onBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val showCreateDirectoryDialog = remember { mutableStateOf(false) }
    val showCompressDialog = remember { mutableStateOf(false) }
    val deleteConfirmCount = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiEvent, uiState.extractDialog) {
        uiEvent.collect { event ->
            when (event) {
                is FileBrowserUiEvent.ShowSnackbar -> {
                    if (uiState.extractDialog != null && event.openExtractJobId != null) {
                        return@collect
                    }
                    val actionLabel = when {
                        event.openExtractJobId != null -> "開く"
                        event.extractDetailJobId != null -> "詳細"
                        event.showAction -> "表示"
                        else -> null
                    }
                    val result = snackbarHostState.showDismissibleSnackbar(
                        message = event.message,
                        actionLabel = actionLabel,
                    )
                    when {
                        result == SnackbarResult.ActionPerformed && event.openExtractJobId != null -> {
                            onOpenExtractResult(event.openExtractJobId)
                        }

                        result == SnackbarResult.ActionPerformed && event.extractDetailJobId != null -> {
                            onNavigateToExtractDetail(event.extractDetailJobId)
                        }

                        result == SnackbarResult.ActionPerformed && event.showAction -> {
                            onNavigateToUploadProgress()
                        }
                    }
                }
                is FileBrowserUiEvent.ShowCreateDirectoryDialog -> {
                    showCreateDirectoryDialog.value = true
                }
                is FileBrowserUiEvent.ShowCompressDialog -> {
                    showCompressDialog.value = true
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
            showCompressDialog = showCompressDialog.value,
            onCompressDialogDismiss = { showCompressDialog.value = false },
            onConfirmCompress = { fileName ->
                uiState.callbacks.onConfirmCompress(fileName)
                showCompressDialog.value = false
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
