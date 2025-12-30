package net.matsudamper.folderviewer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.repository.StorageConfiguration

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreenContent(
        storages = listOf(
            StorageConfiguration.Smb(
                id = "1",
                name = "My NAS",
                ip = "192.168.1.10",
                username = "user",
            ),
        ),
        onNavigateToSettings = {},
        onAddStorageClick = {},
        onStorageClick = {},
        onEditStorageClick = {},
    )
}
