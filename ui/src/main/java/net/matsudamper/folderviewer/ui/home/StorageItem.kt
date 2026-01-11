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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StorageItem(
    storage: UiStorageConfiguration,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                storage = storage,
                showMenu = showMenu,
                onDismiss = { showMenu = false },
                onEditClick = {
                    showMenu = false
                    onEditClick()
                },
                onDeleteClick = {
                    showMenu = false
                    showDeleteDialog = true
                },
            )
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            storageName = storage.name,
            onConfirm = {
                showDeleteDialog = false
                onDeleteClick()
            },
            onDismiss = {
                showDeleteDialog = false
            },
        )
    }
}

@Composable
private fun StorageItemContent(
    storage: UiStorageConfiguration,
) {
    Column(modifier = Modifier.padding(PaddingNormal)) {
        Text(text = storage.name, style = MaterialTheme.typography.titleMedium)
        val type = when (storage) {
            is UiStorageConfiguration.Smb -> "SMB: ${storage.ip}"
            is UiStorageConfiguration.Local -> "ローカル: ${storage.rootPath}"
            is UiStorageConfiguration.SharePoint -> "SharePoint: ${storage.objectId}"
        }
        Text(text = type, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StorageItemMenu(
    storage: UiStorageConfiguration,
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = onDismiss,
    ) {
        when (storage) {
            is UiStorageConfiguration.Smb -> {
                DropdownMenuItem(
                    text = { Text("編集") },
                    onClick = onEditClick,
                )
                DropdownMenuItem(
                    text = { Text("削除") },
                    onClick = onDeleteClick,
                )
            }

            is UiStorageConfiguration.SharePoint -> {
                DropdownMenuItem(
                    text = { Text("編集") },
                    onClick = onEditClick,
                )
                DropdownMenuItem(
                    text = { Text("削除") },
                    onClick = onDeleteClick,
                )
            }

            is UiStorageConfiguration.Local -> {
                DropdownMenuItem(
                    text = { Text("削除") },
                    onClick = onDeleteClick,
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    storageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ストレージを削除") },
        text = { Text("「$storageName」を削除しますか？") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("削除")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}
