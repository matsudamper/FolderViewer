package net.matsudamper.folderviewer.ui.storage

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StorageTypeSelectionScreen(
    snackbarHostState: SnackbarHostState,
    onSmbClick: () -> Unit,
    onLocalClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            StorageTypeSelectionTopBar(onBack = onBack)
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        StorageTypeSelectionBody(
            modifier = Modifier.padding(innerPadding),
            onSmbClick = onSmbClick,
            onLocalClick = onLocalClick,
        )
    }
}
