package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeScreenContent(
    storages: List<UiStorageConfiguration>,
    onNavigateToSettings: () -> Unit,
    onAddStorageClick: () -> Unit,
    onStorageClick: (UiStorageConfiguration) -> Unit,
    onEditStorageClick: (UiStorageConfiguration) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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
            contentPadding = innerPadding,
        )
    }
}
