package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults
import net.matsudamper.folderviewer.ui.util.plus

internal val PaddingNormal: androidx.compose.ui.unit.Dp = 16.dp
internal val PaddingSmall: androidx.compose.ui.unit.Dp = 8.dp

@Composable
public fun HomeScreen(
    uiState: HomeUiState,
) {
    HomeScreen(
        storages = uiState.storages,
        onNavigateToSettings = { uiState.callbacks.onNavigateToSettings() },
        onNavigateToUploadProgress = { uiState.callbacks.onNavigateToUploadProgress() },
        onAddStorageClick = { uiState.callbacks.onAddStorageClick() },
        onStorageClick = { uiState.callbacks.onStorageClick(it) },
        onEditStorageClick = { uiState.callbacks.onEditStorageClick(it) },
        onDeleteStorageClick = { uiState.callbacks.onDeleteStorageClick(it) },
    )
}

@Composable
private fun HomeScreen(
    storages: List<UiStorageConfiguration>,
    onNavigateToSettings: () -> Unit,
    onNavigateToUploadProgress: () -> Unit,
    onAddStorageClick: () -> Unit,
    onStorageClick: (UiStorageConfiguration) -> Unit,
    onEditStorageClick: (UiStorageConfiguration) -> Unit,
    onDeleteStorageClick: (StorageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fabHeight by remember { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        topBar = {
            HomeTopBar(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToUploadProgress = onNavigateToUploadProgress,
            )
        },
        floatingActionButton = {
            Box(modifier = Modifier.onSizeChanged { fabHeight = it.height }) {
                HomeFloatingToolbar(onAddStorageClick = onAddStorageClick)
            }
        },
    ) { innerPadding ->
        val contentPadding = if (fabHeight > 0) {
            innerPadding.plus(
                PaddingValues(bottom = with(LocalDensity.current) { fabHeight.toDp() } + 16.dp),
            )
        } else {
            innerPadding
        }
        StorageList(
            storages = storages,
            onStorageClick = onStorageClick,
            onEditStorageClick = onEditStorageClick,
            onDeleteStorageClick = onDeleteStorageClick,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HomeFloatingToolbar(
    onAddStorageClick: () -> Unit,
) {
    FloatingActionButton(onClick = onAddStorageClick) {
        Icon(
            painter = painterResource(id = R.drawable.ic_add),
            contentDescription = "Add Storage",
        )
    }
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
