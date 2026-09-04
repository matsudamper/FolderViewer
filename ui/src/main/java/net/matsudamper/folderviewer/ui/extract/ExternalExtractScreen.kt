package net.matsudamper.folderviewer.ui.extract

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode
import net.matsudamper.folderviewer.ui.browser.FileBrowserExtractDialog

@Composable
fun ExternalExtractScreen(
    uiState: ExternalExtractUiState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        uiState.callbacks.onDismissRequest()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FileBrowserExtractDialog(
                defaultName = uiState.defaultName,
                mode = uiState.mode,
                isExtracting = uiState.isExtracting,
                hintMessage = uiState.locationMessage,
                onDismissRequest = uiState.callbacks::onDismissRequest,
                onConfirm = uiState.callbacks::onConfirm,
            )
        }
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
            locationMessage = "元の場所に書き込めないため、Documents/FolderViewer に展開します",
            callbacks = object : ExternalExtractUiState.Callbacks {
                override fun onDismissRequest() = Unit

                override fun onConfirm(outputName: String) = Unit
            },
        ),
        snackbarHostState = SnackbarHostState(),
    )
}
