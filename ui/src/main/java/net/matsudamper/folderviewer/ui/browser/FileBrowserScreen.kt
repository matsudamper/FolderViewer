package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    uiEvent: Flow<FileBrowserUiEvent>,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        uiState.callbacks.onBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiEvent) {
        uiEvent.collect { event ->
            when (event) {
                is FileBrowserUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Box(modifier = modifier) {
        FileBrowserScreenContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
        )
    }
}
