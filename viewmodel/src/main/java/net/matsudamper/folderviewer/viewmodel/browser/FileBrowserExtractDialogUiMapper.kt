package net.matsudamper.folderviewer.viewmodel.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState

internal fun FileBrowserExtractCoordinator.closeDialog(
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
) {
    stopProgressObservation()
    viewModelStateFlow.update { it.copy(extractDialog = null) }
}

internal fun FileBrowserExtractCoordinator.showExtractDialogResult(
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
    jobId: Long,
    message: String,
) {
    stopProgressObservation()
    viewModelStateFlow.update { state ->
        val dialog = state.extractDialog ?: return@update state
        if (dialog.jobId != jobId) return@update state
        state.copy(
            extractDialog = dialog.copy(
                isExtracting = false,
                resultMessage = message,
            ),
        )
    }
}

internal fun mapExtractDialogUiState(
    dialog: FileBrowserViewModel.ViewModelState.ExtractDialogState,
    progress: ExtractDialogProgress?,
): FileBrowserUiState.ExtractDialogState {
    return FileBrowserUiState.ExtractDialogState(
        folderName = dialog.folderName,
        isExtracting = dialog.isExtracting,
        jobId = dialog.jobId,
        mode = dialog.mode,
        progress = progress?.progress,
        progressText = progress?.progressText,
        resultMessage = dialog.resultMessage,
        isAwaitingExtractPermission = dialog.isAwaitingExtractPermission,
    )
}
