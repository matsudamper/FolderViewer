package net.matsudamper.folderviewer.ui.browser

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
                title = uiState.title,
                onBack = callbacks::onBack,
                sortConfig = uiState.sortConfig,
                onSortConfigChange = callbacks::onSortConfigChanged,
                displayMode = uiState.displayMode,
                onDisplayModeChange = callbacks::onDisplayModeChanged,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        FileBrowserBody(
            modifier = Modifier,
            uiState = uiState,
            imageLoader = imageLoader,
            onRefresh = callbacks::onRefresh,
            contentPadding = innerPadding,
        )
    }
}
