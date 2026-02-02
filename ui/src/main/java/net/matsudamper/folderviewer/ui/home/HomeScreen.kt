package net.matsudamper.folderviewer.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@Composable
public fun HomeScreen(
    uiState: HomeUiState,
) {
    HomeScreenContent(
        storages = uiState.storages,
        onNavigateToSettings = { uiState.callbacks.onNavigateToSettings() },
        onNavigateToUploadProgress = { uiState.callbacks.onNavigateToUploadProgress() },
        onAddStorageClick = { uiState.callbacks.onAddStorageClick() },
        onStorageClick = { uiState.callbacks.onStorageClick(it) },
        onEditStorageClick = { uiState.callbacks.onEditStorageClick(it) },
        onDeleteStorageClick = { uiState.callbacks.onDeleteStorageClick(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(
    onNavigateToSettings: () -> Unit,
    onNavigateToUploadProgress: () -> Unit,
) {
    TopAppBar(
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = { Text("FolderViewer") },
        actions = {
            IconButton(onClick = onNavigateToUploadProgress) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_upload_file),
                    contentDescription = "Upload Progress",
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = "Settings",
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun HomeFloatingToolbar(
    onAddStorageClick: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onAddStorageClick,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Add Storage",
                )
            }
        },
        content = {},
    )
}

internal val PaddingNormal: androidx.compose.ui.unit.Dp = 16.dp
internal val PaddingSmall: androidx.compose.ui.unit.Dp = 8.dp
