package net.matsudamper.folderviewer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        uiState = HomeUiState(
            storages = listOf(
                UiStorageConfiguration.Smb(
                    id = "1",
                    name = "My NAS",
                    ip = "192.168.1.10",
                    username = "user",
                ),
            ),
        ),
        onNavigateToSettings = {},
        onAddStorageClick = {},
        onStorageClick = {},
        onEditStorageClick = {},
        onDeleteStorageClick = {},
    )
}
