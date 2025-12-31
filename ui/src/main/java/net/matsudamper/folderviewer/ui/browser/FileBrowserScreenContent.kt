package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.ImageLoader

@Composable
internal fun FileBrowserScreenContent(
    uiState: FileBrowserUiState,
    imageLoader: ImageLoader,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val callbacks = uiState.callbacks
    Scaffold(
        modifier = modifier,
        topBar = {
            FileBrowserTopBar(
                currentPath = uiState.currentPath,
                onBack = callbacks.onBack,
                onUpClick = callbacks.onUpClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        FileBrowserBody(
            modifier = Modifier.padding(innerPadding),
            uiState = uiState,
            imageLoader = imageLoader,
            onFileClick = callbacks.onFileClick,
            onRefresh = callbacks.onRefresh,
        )
    }
}
