package net.matsudamper.folderviewer.ui.browser

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import net.matsudamper.folderviewer.ui.R

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
                isFavorite = uiState.isFavorite,
                visibleFavoriteButton = uiState.visibleFavoriteButton,
                onBack = callbacks::onBack,
                sortConfig = uiState.sortConfig,
                onSortConfigChange = callbacks::onSortConfigChanged,
                displayConfig = uiState.displayConfig,
                onDisplayConfigChange = callbacks::onDisplayModeChanged,
                onFavoriteClick = callbacks::onFavoriteClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.visibleFolderBrowserButton) {
                FloatingActionButton(
                    onClick = { uiState.callbacks.onFolderBrowserClick() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_eye),
                        contentDescription = "Folder Browser",
                    )
                }
            }
        },
    ) { innerPadding ->
        FileBrowserBody(
            modifier = Modifier,
            uiState = uiState,
            onRefresh = callbacks::onRefresh,
            contentPadding = innerPadding,
        )
    }
}
