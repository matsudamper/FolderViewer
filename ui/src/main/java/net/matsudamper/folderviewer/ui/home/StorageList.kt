package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.folderviewer.repository.StorageConfiguration

@Composable
internal fun StorageList(
    storages: List<StorageConfiguration>,
    onStorageClick: (StorageConfiguration) -> Unit,
    onEditStorageClick: (StorageConfiguration) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PaddingNormal),
            verticalArrangement = Arrangement.spacedBy(PaddingSmall),
        ) {
            items(storages) { storage ->
                StorageItem(
                    storage = storage,
                    onClick = { onStorageClick(storage) },
                    onEditClick = { onEditStorageClick(storage) },
                )
            }
        }
    }
}
