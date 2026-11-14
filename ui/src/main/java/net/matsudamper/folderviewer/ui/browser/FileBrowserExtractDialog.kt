package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.util.LinkedMessageText

@Composable
internal fun FileBrowserExtractDialog(
    uiState: ExtractDialogUiState,
) {
    val callbacks = uiState.callbacks
    var extractNameInput by remember(uiState.defaultName, uiState.mode) {
        mutableStateOf(uiState.defaultName)
    }
    var deleteSourceFile by remember(uiState.defaultName, uiState.mode, uiState.deleteSourceCheckboxVisible) {
        mutableStateOf(false)
    }
    val dialogTitle = when (uiState.mode) {
        ExtractDialogMode.ZipFolder -> "アーカイブを展開"
        ExtractDialogMode.ZstFile -> "zstを展開"
        ExtractDialogMode.XzFile -> "xzを展開"
    }
    val nameLabel = when (uiState.mode) {
        ExtractDialogMode.ZipFolder -> "フォルダ名"

        ExtractDialogMode.ZstFile,
        ExtractDialogMode.XzFile,
        -> "ファイル名"
    }
    val runResultAction: (() -> Unit) -> Unit = { action ->
        if (uiState.deleteSourceCheckboxVisible && deleteSourceFile) {
            callbacks.onDeleteSourceRequested()
        }
        action()
    }
    AlertDialog(
        onDismissRequest = callbacks::onDismissRequest,
        title = { Text(dialogTitle) },
        text = {
            Column {
                uiState.hintMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (uiState.nameInputVisible) {
                    TextField(
                        value = extractNameInput,
                        onValueChange = { extractNameInput = it },
                        label = { Text(nameLabel) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (uiState.progressVisible) {
                    val progress = uiState.progress
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
                        text = uiState.progressText ?: "解凍中...",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                uiState.statusMessage?.let { message ->
                    LinkedMessageText(
                        message = message,
                        linkText = uiState.statusMessageLinkText,
                        onLinkClick = callbacks::onStatusMessageLinkClick,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        textColor = Color.Unspecified,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (uiState.deleteSourceCheckboxVisible) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .toggleable(
                                value = deleteSourceFile,
                                role = Role.Checkbox,
                                onValueChange = { deleteSourceFile = it },
                            ),
                    ) {
                        Checkbox(
                            checked = deleteSourceFile,
                            onCheckedChange = null,
                        )
                        Text("元のファイルを削除する")
                    }
                }
            }
        },
        confirmButton = {
            if (uiState.resultButtonsVisible) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { runResultAction(callbacks::onOpenDetail) },
                        enabled = uiState.resultButtonsEnabled,
                    ) {
                        Text("詳細")
                    }
                    TextButton(
                        onClick = { runResultAction(callbacks::onOpenResult) },
                        enabled = uiState.openButtonEnabled,
                    ) {
                        Text("開く")
                    }
                }
            } else {
                TextButton(
                    onClick = {
                        if (extractNameInput.isNotBlank()) {
                            callbacks.onConfirm(extractNameInput)
                        }
                    },
                    enabled = extractNameInput.isNotBlank(),
                ) {
                    Text("解凍")
                }
            }
        },
        dismissButton = {
            if (uiState.resultButtonsVisible) {
                TextButton(
                    onClick = { runResultAction(callbacks::onClose) },
                    enabled = uiState.resultButtonsEnabled,
                ) {
                    Text("閉じる")
                }
            } else {
                TextButton(
                    onClick = callbacks::onDismissRequest,
                ) {
                    Text("キャンセル")
                }
            }
        },
    )
}

private object PreviewCallbacks : ExtractDialogUiState.Callbacks {
    override fun onDismissRequest() = Unit
    override fun onClose() = Unit
    override fun onConfirm(outputName: String) = Unit
    override fun onOpenResult() = Unit
    override fun onOpenDetail() = Unit
    override fun onDeleteSourceRequested() = Unit
    override fun onStatusMessageLinkClick() = Unit
}

private fun previewUiState(
    defaultName: String,
    mode: ExtractDialogMode,
    extracting: Boolean,
    complete: Boolean,
    statusMessage: String?,
    statusMessageLinkText: String?,
    progress: Float?,
    progressText: String?,
): ExtractDialogUiState {
    return ExtractDialogUiState(
        defaultName = defaultName,
        mode = mode,
        hintMessage = null,
        nameInputVisible = !extracting && !complete,
        progressVisible = extracting,
        progress = progress,
        progressText = progressText,
        statusMessage = statusMessage,
        statusMessageLinkText = statusMessageLinkText,
        deleteSourceCheckboxVisible = complete,
        resultButtonsVisible = extracting || complete,
        resultButtonsEnabled = true,
        openButtonEnabled = complete,
        callbacks = PreviewCallbacks,
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogZipPreview() {
    FileBrowserExtractDialog(
        uiState = previewUiState(
            defaultName = "archive",
            mode = ExtractDialogMode.ZipFolder,
            extracting = false,
            complete = false,
            statusMessage = null,
            statusMessageLinkText = null,
            progress = null,
            progressText = null,
        ),
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogZstPreview() {
    FileBrowserExtractDialog(
        uiState = previewUiState(
            defaultName = "archive.tar",
            mode = ExtractDialogMode.ZstFile,
            extracting = false,
            complete = false,
            statusMessage = null,
            statusMessageLinkText = null,
            progress = null,
            progressText = null,
        ),
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogXzPreview() {
    FileBrowserExtractDialog(
        uiState = previewUiState(
            defaultName = "archive.tar",
            mode = ExtractDialogMode.XzFile,
            extracting = false,
            complete = false,
            statusMessage = null,
            statusMessageLinkText = null,
            progress = null,
            progressText = null,
        ),
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogExtractingFileCountPreview() {
    FileBrowserExtractDialog(
        uiState = previewUiState(
            defaultName = "archive",
            mode = ExtractDialogMode.ZipFolder,
            extracting = true,
            complete = false,
            statusMessage = null,
            statusMessageLinkText = null,
            progress = 0.35f,
            progressText = "35/100 ファイル",
        ),
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogExtractingBytesPreview() {
    FileBrowserExtractDialog(
        uiState = previewUiState(
            defaultName = "archive.tar.xz",
            mode = ExtractDialogMode.XzFile,
            extracting = true,
            complete = false,
            statusMessage = null,
            statusMessageLinkText = null,
            progress = 0.6f,
            progressText = "12.0 MB/20.0 MB",
        ),
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogFailedPreview() {
    FileBrowserExtractDialog(
        uiState = previewUiState(
            defaultName = "archive",
            mode = ExtractDialogMode.ZipFolder,
            extracting = false,
            complete = false,
            statusMessage = "同じ名前のフォルダが既に存在します: archive",
            statusMessageLinkText = "archive",
            progress = null,
            progressText = null,
        ),
    )
}

@Preview
@Composable
private fun FileBrowserExtractDialogCompletePreview() {
    FileBrowserExtractDialog(
        uiState = previewUiState(
            defaultName = "archive",
            mode = ExtractDialogMode.ZipFolder,
            extracting = false,
            complete = true,
            statusMessage = "archiveに展開しました",
            statusMessageLinkText = null,
            progress = null,
            progressText = null,
        ),
    )
}
