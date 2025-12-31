package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    callbacks: FileBrowserUiState.Callbacks,
    onErrorMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPath = uiState.currentPath
    BackHandler(enabled = currentPath.isNotEmpty()) {
        callbacks.onUpClick()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error
    LaunchedEffect(errorMessage) {
        errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(errorMessage)
        onErrorMessageShown()
    }

    FileBrowserScreenContent(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        callbacks = callbacks,
    )
}
