package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.folderviewer.repository.FileItem

@Composable
internal fun FileBrowserScreenContent(
    uiState: FileBrowserUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onUpClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            FileBrowserTopBar(
                currentPath = uiState.currentPath,
                onBack = onBack,
                onUpClick = onUpClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        FileBrowserBody(
            modifier = Modifier.padding(innerPadding),
            uiState = uiState,
            onFileClick = onFileClick,
        )
    }
}
