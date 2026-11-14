package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.ui.R

@Composable
internal fun FileBrowserRenameDialog(
    input: String,
    confirmButtonEnabled: Boolean,
    onInputChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            TextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text("新しい名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
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
        input = "sample.txt",
        confirmButtonEnabled = false,
        onInputChange = {},
        onDismissRequest = {},
        onConfirm = {},
    )
}
