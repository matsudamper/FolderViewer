package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults
import net.matsudamper.folderviewer.ui.util.plus

@Composable
public fun StoragePickerScreen(
    uiState: StoragePickerUiState,
) {
    Scaffold(
        topBar = {
            StoragePickerTopBar()
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PaddingNormal).plus(innerPadding),
            verticalArrangement = Arrangement.spacedBy(PaddingSmall),
        ) {
            items(uiState.storages) { storage ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uiState.callbacks.onStorageClick(storage) },
                ) {
                    StorageItemContent(storage = storage)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoragePickerTopBar() {
    TopAppBar(
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = { Text("ファイルを選択") },
    )
}
