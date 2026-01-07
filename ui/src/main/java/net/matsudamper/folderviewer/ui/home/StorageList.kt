package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun StorageList(
    storages: List<UiStorageConfiguration>,
    onStorageClick: (UiStorageConfiguration) -> Unit,
    onEditStorageClick: (UiStorageConfiguration) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PaddingNormal,
                end = PaddingNormal,
                top = PaddingNormal + contentPadding.calculateTopPadding(),
                bottom = PaddingNormal + contentPadding.calculateBottomPadding(),
            ),
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
