package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.matsudamper.folderviewer.ui.R

@Composable
internal fun FileBrowserRenameDialog(
    uiState: FileBrowserUiState.RenameDialog,
    onInputChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = uiState.closeEnabled,
            dismissOnClickOutside = uiState.closeEnabled,
        ),
        title = { Text(stringResource(R.string.rename)) },
        text = {
            TextField(
                value = uiState.input,
                onValueChange = onInputChange,
                enabled = uiState.inputEnabled,
                label = { Text("新しい名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = uiState.confirmButtonEnabled,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "変更",
                        modifier = Modifier.alpha(if (uiState.visibleRenamingProgress) 0f else 1f),
                    )
                    if (uiState.visibleRenamingProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = uiState.closeEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
@Preview
private fun FileBrowserRenameDialogPreview() {
    FileBrowserRenameDialog(
        uiState = FileBrowserUiState.RenameDialog(
            input = "sample.txt",
            inputEnabled = true,
            confirmButtonEnabled = false,
            closeEnabled = true,
            visibleRenamingProgress = false,
        ),
        onInputChange = {},
        onDismissRequest = {},
        onConfirm = {},
    )
}

@Composable
@Preview
private fun FileBrowserRenameDialogRenamingPreview() {
    FileBrowserRenameDialog(
        uiState = FileBrowserUiState.RenameDialog(
            input = "renamed.txt",
            inputEnabled = false,
            confirmButtonEnabled = false,
            closeEnabled = false,
            visibleRenamingProgress = true,
        ),
        onInputChange = {},
        onDismissRequest = {},
        onConfirm = {},
    )
}
