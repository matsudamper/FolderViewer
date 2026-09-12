package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun FileBrowserExtractDialog(
    defaultName: String,
    mode: ExtractDialogMode,
    isExtracting: Boolean,
    isExtractComplete: Boolean,
    statusMessage: String?,
    showDeleteSourceOption: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    onOpenResult: () -> Unit,
    onOpenDetail: () -> Unit,
    onDeleteSourceRequested: () -> Unit,
    resultActionsEnabled: Boolean = true,
    onClose: () -> Unit = onDismissRequest,
    hintMessage: String? = null,
    progress: Float? = null,
    progressText: String? = null,
) {
    var extractNameInput by remember(defaultName, mode) {
        mutableStateOf(defaultName)
    }
    var deleteSourceFile by remember(defaultName, mode, isExtractComplete) {
        mutableStateOf(false)
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
    val showResultActions = isExtracting || isExtractComplete
    val runResultAction: (() -> Unit) -> Unit = { action ->
        if (resultActionsEnabled) {
            if (isExtractComplete && showDeleteSourceOption && deleteSourceFile) {
                onDeleteSourceRequested()
            }
            action()
        }
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
                if (!showResultActions) {
                    TextField(
                        value = extractNameInput,
                        onValueChange = { extractNameInput = it },
                        label = { Text(nameLabel) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isExtracting) {
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
                        text = progressText ?: "解凍中...",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (isExtractComplete && showDeleteSourceOption) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Checkbox(
                            checked = deleteSourceFile,
                            onCheckedChange = { deleteSourceFile = it },
                        )
                        Text("元のファイルを削除する")
                    }
                }
            }
        },
        confirmButton = {
            if (showResultActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { runResultAction(onOpenDetail) },
                        enabled = resultActionsEnabled,
                    ) {
                        Text("詳細")
                    }
                    TextButton(
                        onClick = { runResultAction(onOpenResult) },
                        enabled = isExtractComplete && resultActionsEnabled,
                    ) {
                        Text("開く")
                    }
                }
            } else {
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
            TextButton(
                onClick = {
                    if (showResultActions) {
                        runResultAction(onClose)
                    } else {
                        onDismissRequest()
                    }
                },
                enabled = !showResultActions || resultActionsEnabled,
            ) {
                Text(if (showResultActions) "閉じる" else "キャンセル")
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
        isExtractComplete = false,
        statusMessage = null,
        showDeleteSourceOption = true,
        onDismissRequest = {},
        onConfirm = {},
        onOpenResult = {},
        onOpenDetail = {},
        onDeleteSourceRequested = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogZstPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar",
        mode = ExtractDialogMode.ZstFile,
        isExtracting = false,
        isExtractComplete = false,
        statusMessage = null,
        showDeleteSourceOption = true,
        onDismissRequest = {},
        onConfirm = {},
        onOpenResult = {},
        onOpenDetail = {},
        onDeleteSourceRequested = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogXzPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar",
        mode = ExtractDialogMode.XzFile,
        isExtracting = false,
        isExtractComplete = false,
        statusMessage = null,
        showDeleteSourceOption = true,
        onDismissRequest = {},
        onConfirm = {},
        onOpenResult = {},
        onOpenDetail = {},
        onDeleteSourceRequested = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogExtractingFileCountPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        mode = ExtractDialogMode.ZipFolder,
        isExtracting = true,
        isExtractComplete = false,
        statusMessage = null,
        showDeleteSourceOption = true,
        progress = 0.35f,
        progressText = "35/100 ファイル",
        onDismissRequest = {},
        onConfirm = {},
        onOpenResult = {},
        onOpenDetail = {},
        onDeleteSourceRequested = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogExtractingBytesPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive.tar.xz",
        mode = ExtractDialogMode.XzFile,
        isExtracting = true,
        isExtractComplete = false,
        statusMessage = null,
        showDeleteSourceOption = true,
        progress = 0.6f,
        progressText = "12.0 MB/20.0 MB",
        onDismissRequest = {},
        onConfirm = {},
        onOpenResult = {},
        onOpenDetail = {},
        onDeleteSourceRequested = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogFailedPreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        mode = ExtractDialogMode.ZipFolder,
        isExtracting = false,
        isExtractComplete = false,
        statusMessage = "同じ名前のフォルダが既に存在します: archive",
        showDeleteSourceOption = true,
        onDismissRequest = {},
        onConfirm = {},
        onOpenResult = {},
        onOpenDetail = {},
        onDeleteSourceRequested = {},
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogCompletePreview() {
    FileBrowserExtractDialog(
        defaultName = "archive",
        mode = ExtractDialogMode.ZipFolder,
        isExtracting = false,
        isExtractComplete = true,
        statusMessage = "archiveに展開しました",
        showDeleteSourceOption = true,
        onDismissRequest = {},
        onConfirm = {},
        onOpenResult = {},
        onOpenDetail = {},
        onDeleteSourceRequested = {},
    )
}
