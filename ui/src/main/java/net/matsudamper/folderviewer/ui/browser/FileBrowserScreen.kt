package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil.ImageLoader

@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val currentPath = uiState.currentPath
    val callbacks = uiState.callbacks

    BackHandler(enabled = currentPath.isNotEmpty()) {
        callbacks.onUpClick()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error
    LaunchedEffect(errorMessage) {
        errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(errorMessage)
        callbacks.onErrorShown()
    }

    FileBrowserScreenContent(
        modifier = modifier,
        uiState = uiState,
        imageLoader = imageLoader,
        snackbarHostState = snackbarHostState,
    )
}
