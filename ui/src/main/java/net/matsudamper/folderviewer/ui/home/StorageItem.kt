package net.matsudamper.folderviewer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.ui.component.FullWidthDialog
import net.matsudamper.folderviewer.ui.component.FullWidthDialogContent
import net.matsudamper.folderviewer.ui.component.FullWidthDialogTextItem
import net.matsudamper.folderviewer.ui.component.FullWidthDialogTitle

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
        StorageItemContent(storage = storage)
    }

    if (showMenu) {
        FullWidthDialog(
            onDismissRequest = { showMenu = false },
        ) {
            StorageItemMenuItems(
                storage = storage,
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
        FullWidthDialog(
            onDismissRequest = { showDeleteDialog = false },
        ) {
            DeleteConfirmContent(
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
}

@Composable
internal fun StorageItemContent(
    storage: UiStorageConfiguration,
) {
    Column(modifier = Modifier.padding(PaddingNormal)) {
        Text(text = storage.name, style = MaterialTheme.typography.titleMedium)
        val type = when (storage) {
            is UiStorageConfiguration.Smb -> "SMB: ${storage.ip}"
            is UiStorageConfiguration.Local -> "ローカル: ${storage.rootPath}"
            is UiStorageConfiguration.SharePoint -> storage.objectId
        }
        Text(text = type, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColumnScope.StorageItemMenuItems(
    storage: UiStorageConfiguration,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    when (storage) {
        is UiStorageConfiguration.Smb,
        is UiStorageConfiguration.SharePoint,
        -> {
            FullWidthDialogTextItem(
                text = "編集",
                onClick = onEditClick,
            )
            FullWidthDialogTextItem(
                text = "削除",
                onClick = onDeleteClick,
            )
        }

        is UiStorageConfiguration.Local -> {
            FullWidthDialogTextItem(
                text = "削除",
                onClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun ColumnScope.DeleteConfirmContent(
    storageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FullWidthDialogTitle(text = "ストレージを削除")
    Text(
        text = "「$storageName」を削除しますか？",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    FullWidthDialogTextItem(
        text = "削除",
        onClick = onConfirm,
    )
    FullWidthDialogTextItem(
        text = "キャンセル",
        onClick = onDismiss,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStorageItemMenu() {
    MaterialTheme {
        FullWidthDialogContent {
            StorageItemMenuItems(
                storage = UiStorageConfiguration.Smb(
                    id = StorageId("id"),
                    name = "MyStorage",
                    ip = "192.168.1.1",
                    username = "user",
                ),
                onEditClick = {},
                onDeleteClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewStorageDeleteConfirm() {
    MaterialTheme {
        FullWidthDialogContent {
            DeleteConfirmContent(
                storageName = "MyStorage",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
