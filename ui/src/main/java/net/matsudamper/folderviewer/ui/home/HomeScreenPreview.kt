package net.matsudamper.folderviewer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.common.StorageId

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        uiState = HomeUiState(
            storages = listOf(
                UiStorageConfiguration.Smb(
                    id = StorageId("1"),
                    name = "My NAS",
                    ip = "192.168.1.10",
                    username = "user",
                ),
            ),
            callbacks = object : HomeUiState.Callbacks {
                override fun onNavigateToSettings() = Unit
                override fun onNavigateToUploadProgress() = Unit
                override fun onAddStorageClick() = Unit
                override fun onStorageClick(storage: UiStorageConfiguration) = Unit
                override fun onEditStorageClick(storage: UiStorageConfiguration) = Unit
                override fun onDeleteStorageClick(id: StorageId) = Unit
            },
        ),
    )
}
