package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    isExtracting: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    hintMessage: String? = null,
) {
    var extractNameInput by remember(defaultName, mode) {
        mutableStateOf(defaultName)
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
                hintMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text("解凍中...")
                } else {
                    TextField(
                        value = extractNameInput,
                        onValueChange = { extractNameInput = it },
                        label = { Text(nameLabel) },
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
        mode = ExtractDialogMode.ZipFolder,
        isExtracting = false,
        onDismissRequest = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogZstPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar",
        mode = ExtractDialogMode.ZstFile,
        isExtracting = false,
        onDismissRequest = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogXzPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar",
        mode = ExtractDialogMode.XzFile,
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
        mode = ExtractDialogMode.ZipFolder,
        isExtracting = true,
        onDismissRequest = {},
        onConfirm = {},
    )
}
