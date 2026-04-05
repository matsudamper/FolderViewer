package net.matsudamper.folderviewer.ui.permission

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PermissionRequestScreen(
    uiState: PermissionRequestUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
    ) { innerPadding ->
        PermissionRequestContent(
            modifier = Modifier.padding(innerPadding),
            uiState = uiState,
        )
    }
}
