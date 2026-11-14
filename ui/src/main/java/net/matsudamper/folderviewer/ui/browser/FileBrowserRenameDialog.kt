package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.ui.R

@Composable
internal fun FileBrowserRenameDialog(
    currentName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var renameInput by remember(currentName) { mutableStateOf(currentName) }
    val confirmButtonEnabled = renameInput.isNotBlank() && renameInput != currentName
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            TextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                label = { Text("新しい名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(renameInput) },
                enabled = confirmButtonEnabled,
            ) {
                Text("変更")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
@Preview
private fun FileBrowserRenameDialogPreview() {
    FileBrowserRenameDialog(
        currentName = "sample.txt",
        onDismissRequest = {},
        onConfirm = {},
    )
}
