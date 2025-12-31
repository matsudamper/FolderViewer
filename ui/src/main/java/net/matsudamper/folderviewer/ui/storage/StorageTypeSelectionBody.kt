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
    onSmbClick: () -> Unit,
    onSftpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        ListItem(
            headlineContent = { Text("SMB (Windows Share / NAS)") },
            modifier = Modifier.clickable(onClick = onSmbClick),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("SFTP (SSH File Transfer Protocol)") },
            modifier = Modifier.clickable(onClick = onSftpClick),
        )
        HorizontalDivider()
    }
}
