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

internal fun isExtractDialogOpenForJob(
    state: FileBrowserViewModel.ViewModelState,
    jobId: Long,
): Boolean {
    return state.extractDialog?.jobId == jobId
}

internal fun updateExtractDialogOnComplete(
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
    jobId: Long,
    message: String,
) {
    viewModelStateFlow.update { state ->
        val dialog = state.extractDialog ?: return@update state
        if (dialog.jobId != jobId) {
            return@update state
        }
        state.copy(
            extractDialog = dialog.copy(
                isExtracting = false,
                isExtractComplete = true,
                statusMessage = message,
            ),
        )
    }
}

internal fun updateExtractDialogOnFailed(
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
    jobId: Long,
    message: String,
) {
    viewModelStateFlow.update { state ->
        val dialog = state.extractDialog ?: return@update state
        if (dialog.jobId != jobId) {
            return@update state
        }
        state.copy(
            extractDialog = dialog.copy(
                isExtracting = false,
                isExtractComplete = false,
                statusMessage = message,
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
        isExtractComplete = dialog.isExtractComplete,
        statusMessage = dialog.statusMessage,
        jobId = dialog.jobId,
        mode = dialog.mode,
        progress = progress?.progress,
        progressText = progress?.progressText,
    )
}

internal fun createFileBrowserExtractCoordinator(
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
    dependencies: FileBrowserExtractCoordinator.Dependencies,
): FileBrowserExtractCoordinator {
    return FileBrowserExtractCoordinator(
        dependencies = dependencies.copy(
            isExtractDialogOpenForJob = { jobId ->
                isExtractDialogOpenForJob(viewModelStateFlow.value, jobId)
            },
            updateExtractDialogOnComplete = { jobId, message ->
                updateExtractDialogOnComplete(
                    viewModelStateFlow = viewModelStateFlow,
                    jobId = jobId,
                    message = message,
                )
            },
            updateExtractDialogOnFailed = { jobId, message ->
                updateExtractDialogOnFailed(
                    viewModelStateFlow = viewModelStateFlow,
                    jobId = jobId,
                    message = message,
                )
            },
        ),
    )
}
