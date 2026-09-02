package net.matsudamper.folderviewer.ui.extract

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode
import net.matsudamper.folderviewer.ui.browser.FileBrowserExtractDialog

@Composable
fun ExternalExtractScreen(
    uiState: ExternalExtractUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        uiState.locationMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        FileBrowserExtractDialog(
            defaultName = uiState.defaultName,
            mode = uiState.mode,
            isExtracting = uiState.isExtracting,
            onDismissRequest = uiState.callbacks::onDismissRequest,
            onConfirm = uiState.callbacks::onConfirm,
        )
    }
}

@Preview
@Composable
private fun ExternalExtractScreenPreview() {
    ExternalExtractScreen(
        uiState = ExternalExtractUiState(
            defaultName = "archive",
            mode = ExtractDialogMode.ZipFolder,
            isExtracting = false,
            locationMessage = "元の場所に書き込めないため、アプリ内の一時フォルダに展開します",
            callbacks = object : ExternalExtractUiState.Callbacks {
                override fun onDismissRequest() = Unit

                override fun onConfirm(outputName: String) = Unit
            },
        ),
    )
}
