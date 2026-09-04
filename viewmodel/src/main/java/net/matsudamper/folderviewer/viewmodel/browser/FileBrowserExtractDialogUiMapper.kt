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
    )
}
