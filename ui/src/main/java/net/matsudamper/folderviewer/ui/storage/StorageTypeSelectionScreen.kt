package net.matsudamper.folderviewer.ui.storage

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StorageTypeSelectionScreen(
    onSmbClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            StorageTypeSelectionTopBar(onBack = onBack)
        },
    ) { innerPadding ->
        StorageTypeSelectionBody(
            modifier = Modifier.padding(innerPadding),
            onSmbClick = onSmbClick,
        )
    }
}
