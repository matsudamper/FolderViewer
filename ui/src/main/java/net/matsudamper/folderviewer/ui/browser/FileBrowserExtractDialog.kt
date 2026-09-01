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
    mode: ExtractDialogMode,
    onDismissRequest: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var extractNameInput by remember(defaultName, mode) {
        mutableStateOf(defaultName)
    }
    var openOnComplete by remember(defaultName, mode) {
        mutableStateOf(true)
    }
    val dialogTitle = when (mode) {
        ExtractDialogMode.ZipFolder -> "zipを展開"
        ExtractDialogMode.ZstFile -> "zstを展開"
        ExtractDialogMode.XzFile -> "xzを展開"
    }
    val nameLabel = when (mode) {
        ExtractDialogMode.ZipFolder -> "フォルダ名"
        ExtractDialogMode.ZstFile,
        ExtractDialogMode.XzFile,
        -> "ファイル名"
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(dialogTitle) },
        text = {
            Column {
                TextField(
                    value = extractNameInput,
                    onValueChange = { extractNameInput = it },
                    label = { Text(nameLabel) },
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
        mode = ExtractDialogMode.ZipFolder,
        onDismissRequest = {},
        onConfirm = { _, _ -> },
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogZstPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar",
        mode = ExtractDialogMode.ZstFile,
        onDismissRequest = {},
        onConfirm = { _, _ -> },
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogXzPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar",
        mode = ExtractDialogMode.XzFile,
        onDismissRequest = {},
        onConfirm = { _, _ -> },
    )
}
