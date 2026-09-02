package net.matsudamper.folderviewer.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun StorageTypeSelectionBody(
    uiState: StorageTypeSelectionUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = { uiState.callbacks.onSmbClick() }),
        ) {
            Text("SMB (Windows Share / NAS)")
        }
        HorizontalDivider()

        ListItem(
            modifier = Modifier.clickable(onClick = { uiState.callbacks.onLocalClick() }),
            supportingContent = { Text("デバイス内のフォルダを参照") },
        ) {
            Text("ローカルストレージ")
        }
        HorizontalDivider()

        ListItem(
            modifier = Modifier.clickable(onClick = { uiState.callbacks.onSharePointClick() }),
            supportingContent = { Text("SharePoint Online のドキュメントライブラリにアクセス") },
        ) {
            Text("SharePoint")
        }
        HorizontalDivider()
    }
}
