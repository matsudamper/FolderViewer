package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun FileBrowserExtractDialog(
    defaultName: String,
    mode: ExtractDialogMode,
    isExtracting: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    hintMessage: String? = null,
    progress: Float? = null,
    progressText: String? = null,
    resultMessage: String? = null,
    isAwaitingExtractPermission: Boolean = false,
    onCancel: () -> Unit,
) {
    var extractNameInput by remember(defaultName, mode) {
        mutableStateOf(defaultName)
    }
    val dialogTitle = when (mode) {
        ExtractDialogMode.ZipFolder -> "アーカイブを展開"
        ExtractDialogMode.ZstFile -> "zstを展開"
        ExtractDialogMode.XzFile -> "xzを展開"
    }
    val nameLabel = when (mode) {
        ExtractDialogMode.ZipFolder -> "フォルダ名"

        ExtractDialogMode.ZstFile,
        ExtractDialogMode.XzFile,
        -> "ファイル名"
    }
    val showResult = resultMessage != null
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = !isAwaitingExtractPermission,
            dismissOnBackPress = !isAwaitingExtractPermission,
        ),
        title = { Text(dialogTitle) },
        text = {
            Column {
                hintMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when {
                    showResult -> {
                        Text(
                            text = resultMessage,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    isExtracting -> {
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                        Text(
                            text = progressText ?: if (isAwaitingExtractPermission) {
                                "準備中..."
                            } else {
                                "解凍中..."
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    else -> {
                        TextField(
                            value = extractNameInput,
                            onValueChange = { extractNameInput = it },
                            label = { Text(nameLabel) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isExtracting && !showResult) {
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
            TextButton(onClick = onCancel) {
                Text(if (isExtracting || showResult) "閉じる" else "キャンセル")
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
        onCancel = {},
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
        onCancel = {},
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
        onCancel = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogExtractingFileCountPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        mode = ExtractDialogMode.ZipFolder,
        isExtracting = true,
        progress = 0.35f,
        progressText = "35/100 ファイル",
        onDismissRequest = {},
        onCancel = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogResultPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        mode = ExtractDialogMode.ZipFolder,
        isExtracting = false,
        resultMessage = "archiveに展開しました",
        onDismissRequest = {},
        onCancel = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogExtractingBytesPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar.xz",
        mode = ExtractDialogMode.XzFile,
        isExtracting = true,
        progress = 0.6f,
        progressText = "12.0 MB/20.0 MB",
        onDismissRequest = {},
        onCancel = {},
        onConfirm = {},
    )
}
