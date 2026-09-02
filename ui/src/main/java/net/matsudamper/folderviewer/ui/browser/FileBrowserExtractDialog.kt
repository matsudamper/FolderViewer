package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
    isExtracting: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var extractNameInput by remember(defaultName) {
        mutableStateOf(defaultName)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("zipを展開") },
        text = {
            Column {
                if (isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text("解凍中...")
                } else {
                    TextField(
                        value = extractNameInput,
                        onValueChange = { extractNameInput = it },
                        label = { Text("フォルダ名") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (!isExtracting) {
                TextButton(
                    onClick = {
                        if (extractNameInput.isNotBlank()) {
                            onConfirm(extractNameInput)
                        }
                    },
                    enabled = extractNameInput.isNotBlank(),
                ) {
                    Text("解凍")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(if (isExtracting) "閉じる" else "キャンセル")
            }
        },
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogZipPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        isExtracting = false,
        onDismissRequest = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogExtractingPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        isExtracting = true,
        onDismissRequest = {},
        onConfirm = {},
    )
}
