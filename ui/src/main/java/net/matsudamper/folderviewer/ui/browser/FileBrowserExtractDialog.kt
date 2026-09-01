package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun FileBrowserExtractDialog(
    defaultName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var extractNameInput by remember(defaultName) {
        mutableStateOf(defaultName)
    }
    var openOnComplete by remember(defaultName) {
        mutableStateOf(true)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("zipを展開") },
        text = {
            Column {
                TextField(
                    value = extractNameInput,
                    onValueChange = { extractNameInput = it },
                    label = { Text("フォルダ名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = openOnComplete,
                        onCheckedChange = { openOnComplete = it },
                    )
                    Text("完了後に開く")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (extractNameInput.isNotBlank()) {
                        onConfirm(extractNameInput, openOnComplete)
                    }
                },
                enabled = extractNameInput.isNotBlank(),
            ) {
                Text("解凍")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("キャンセル")
            }
        },
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogZipPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        onDismissRequest = {},
        onConfirm = { _, _ -> },
    )
}
