package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.matsudamper.folderviewer.repository.StorageConfiguration

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StorageItem(
    storage: StorageConfiguration,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true },
            ),
    ) {
        Box {
            StorageItemContent(storage = storage)
            StorageItemMenu(
                showMenu = showMenu,
                onDismiss = { showMenu = false },
                onEditClick = onEditClick,
            )
        }
    }
}

@Composable
private fun StorageItemContent(
    storage: StorageConfiguration,
) {
    Column(modifier = Modifier.padding(PaddingNormal)) {
        Text(text = storage.name, style = MaterialTheme.typography.titleMedium)
        val type = when (storage) {
            is StorageConfiguration.Smb -> "SMB: ${storage.ip}"
        }
        Text(text = type, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StorageItemMenu(
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
) {
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("Edit") },
            onClick = {
                onDismiss()
                onEditClick()
            },
        )
    }
}
