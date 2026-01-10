package net.matsudamper.folderviewer.ui.storage

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StorageTypeSelectionScreen(
    uiState: StorageTypeSelectionUiState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            StorageTypeSelectionTopBar(onBack = { uiState.callbacks.onBack() })
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        StorageTypeSelectionBody(
            modifier = Modifier.padding(innerPadding),
            uiState = uiState,
        )
    }
}
