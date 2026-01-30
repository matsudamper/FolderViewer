package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.ui.util.plus

@Composable
internal fun StorageList(
    storages: List<UiStorageConfiguration>,
    onStorageClick: (UiStorageConfiguration) -> Unit,
    onEditStorageClick: (UiStorageConfiguration) -> Unit,
    onDeleteStorageClick: (StorageId) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PaddingNormal).plus(contentPadding),
            verticalArrangement = Arrangement.spacedBy(PaddingSmall),
        ) {
            items(storages) { storage ->
                StorageItem(
                    storage = storage,
                    onClick = { onStorageClick(storage) },
                    onEditClick = { onEditStorageClick(storage) },
                    onDeleteClick = { onDeleteStorageClick(storage.id) },
                )
            }
        }
    }
}
