package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.ui.R

@Composable
fun HomeScreen(
    storageRepository: StorageRepository,
    onNavigateToSettings: () -> Unit,
    onAddStorageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel<HomeViewModel>(
        initializer = {
            HomeViewModel(storageRepository)
        },
    )
    val storages by viewModel.storages.collectAsState()

    HomeScreenContent(
        storages = storages,
        onNavigateToSettings = onNavigateToSettings,
        onAddStorageClick = onAddStorageClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    storages: List<StorageConfiguration>,
    onNavigateToSettings: () -> Unit,
    onAddStorageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddStorageClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Add Storage",
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(PaddingNormal),
                verticalArrangement = Arrangement.spacedBy(PaddingSmall),
            ) {
                items(storages) { storage ->
                    StorageItem(storage = storage)
                }
            }
        }
    }
}

@Composable
fun StorageItem(
    storage: StorageConfiguration,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(PaddingNormal)) {
            Text(text = storage.name, style = MaterialTheme.typography.titleMedium)
            val type = when (storage) {
                is StorageConfiguration.Smb -> "SMB: ${storage.ip}"
            }
            Text(text = type, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

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
    )
}

private val PaddingNormal = 16.dp
private val PaddingSmall = 8.dp
