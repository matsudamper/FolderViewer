package net.matsudamper.folderviewer.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.ui.R

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToSettings: () -> Unit,
    onAddStorageClick: () -> Unit,
    onStorageClick: (StorageConfiguration) -> Unit,
    onEditStorageClick: (StorageConfiguration) -> Unit,
) {
    HomeScreenContent(
        storages = uiState.storages,
        onNavigateToSettings = onNavigateToSettings,
        onAddStorageClick = onAddStorageClick,
        onStorageClick = onStorageClick,
        onEditStorageClick = onEditStorageClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(
    onNavigateToSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text("FolderViewer") },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = "Settings",
                )
            }
        },
    )
}

@Composable
internal fun HomeFab(
    onAddStorageClick: () -> Unit,
) {
    FloatingActionButton(onClick = onAddStorageClick) {
        Icon(
            painter = painterResource(id = R.drawable.ic_add),
            contentDescription = "Add Storage",
        )
    }
}

internal val PaddingNormal = 16.dp
internal val PaddingSmall = 8.dp
