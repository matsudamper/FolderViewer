package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.folderviewer.repository.StorageConfiguration

@Composable
fun HomeScreenContent(
    storages: List<StorageConfiguration>,
    onNavigateToSettings: () -> Unit,
    onAddStorageClick: () -> Unit,
    onStorageClick: (StorageConfiguration) -> Unit,
    onEditStorageClick: (StorageConfiguration) -> Unit,
) {
    Scaffold(
        topBar = {
            HomeTopBar(onNavigateToSettings = onNavigateToSettings)
        },
        floatingActionButton = {
            HomeFab(onAddStorageClick = onAddStorageClick)
        },
    ) { innerPadding ->
        StorageList(
            modifier = Modifier.padding(innerPadding),
            storages = storages,
            onStorageClick = onStorageClick,
            onEditStorageClick = onEditStorageClick,
        )
    }
}
