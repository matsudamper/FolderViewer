package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
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
    showPasteFooter: Boolean,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        uiState.callbacks.onBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val showCreateDirectoryDialog = remember { mutableStateOf(false) }

    LaunchedEffect(uiEvent) {
        uiEvent.collect { event ->
            when (event) {
                is FileBrowserUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is FileBrowserUiEvent.ShowCreateDirectoryDialog -> {
                    showCreateDirectoryDialog.value = true
                }
            }
        }
    }

    Box(modifier = modifier) {
        FileBrowserScreenContent(
            uiState = uiState,
            showPasteFooter = showPasteFooter,
            snackbarHostState = snackbarHostState,
            showCreateDirectoryDialog = showCreateDirectoryDialog.value,
            onCreateDirectoryDialogDismiss = { showCreateDirectoryDialog.value = false },
            onConfirmCreateDirectory = { directoryName ->
                uiState.callbacks.onConfirmCreateDirectory(directoryName)
                showCreateDirectoryDialog.value = false
            },
        )
    }
}
