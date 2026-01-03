package net.matsudamper.folderviewer.ui.browser

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun FileBrowserScreenContent(
    uiState: FileBrowserUiState,
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
                displayConfig = uiState.displayConfig,
                onDisplayConfigChange = callbacks::onDisplayModeChanged,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        FileBrowserBody(
            modifier = Modifier,
            uiState = uiState,
            onRefresh = callbacks::onRefresh,
            contentPadding = innerPadding,
        )
    }
}
