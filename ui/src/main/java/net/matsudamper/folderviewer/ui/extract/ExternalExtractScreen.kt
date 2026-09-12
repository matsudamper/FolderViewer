package net.matsudamper.folderviewer.ui.extract

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode
import net.matsudamper.folderviewer.ui.browser.FileBrowserExtractDialog

@Composable
fun ExternalExtractScreen(
    uiState: ExternalExtractUiState,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        uiState.callbacks.onDismissRequest()
    }

    Box(modifier = modifier.fillMaxSize()) {
        FileBrowserExtractDialog(
            defaultName = uiState.defaultName,
            mode = uiState.mode,
            isExtracting = uiState.isExtracting,
            isExtractComplete = uiState.isExtractComplete,
            statusMessage = uiState.statusMessage,
            showDeleteSourceOption = uiState.canDeleteSource,
            resultActionsEnabled = !uiState.isResultActionInProgress,
            hintMessage = uiState.locationMessage,
            progress = uiState.progress,
            progressText = uiState.progressText,
            onDismissRequest = uiState.callbacks::onDismissRequest,
            onClose = uiState.callbacks::onClose,
            onConfirm = uiState.callbacks::onConfirm,
            onOpenResult = uiState.callbacks::onOpenResult,
            onOpenDetail = uiState.callbacks::onOpenDetail,
            onDeleteSourceRequested = uiState.callbacks::onDeleteSourceRequested,
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
            isExtractComplete = true,
            statusMessage = "archiveに展開しました",
            locationMessage = null,
            canDeleteSource = true,
            isResultActionInProgress = false,
            callbacks = object : ExternalExtractUiState.Callbacks {
                override fun onDismissRequest() = Unit

                override fun onClose() = Unit

                override fun onConfirm(outputName: String) = Unit

                override fun onOpenResult() = Unit

                override fun onOpenDetail() = Unit

                override fun onDeleteSourceRequested() = Unit
            },
        ),
    )
}
