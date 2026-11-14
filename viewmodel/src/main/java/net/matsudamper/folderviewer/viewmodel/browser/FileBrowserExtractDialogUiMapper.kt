package net.matsudamper.folderviewer.viewmodel.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.matsudamper.folderviewer.ui.browser.ExtractDialogUiState
import net.matsudamper.folderviewer.viewmodel.util.ExtractOutputLocationResolver

internal fun FileBrowserExtractCoordinator.closeDialog(
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
) {
    stopProgressObservation()
    viewModelStateFlow.update { it.copy(extractDialog = null) }
}

internal suspend fun FileBrowserExtractCoordinator.openResultAndUpdateDialog(
    jobId: Long,
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
) {
    val opened = openExtractResult(
        jobId = jobId,
        errorMessage = viewModelStateFlow.value.extractDialog?.statusMessage,
    )
    if (opened) {
        if (viewModelStateFlow.value.extractDialog?.jobId == jobId) {
            closeDialog(viewModelStateFlow)
        }
        return
    }
    viewModelStateFlow.update { state ->
        val dialog = state.extractDialog ?: return@update state
        if (dialog.jobId != jobId) {
            return@update state
        }
        state.copy(
            extractDialog = dialog.copy(
                statusMessage = "解凍結果を開けませんでした",
            ),
        )
    }
}

internal fun isExtractDialogOpenForJob(
    state: FileBrowserViewModel.ViewModelState,
    jobId: Long,
): Boolean {
    val dialog = state.extractDialog ?: return false
    if (dialog.jobId == jobId) {
        return true
    }
    return dialog.isExtracting && dialog.jobId == null
}

internal fun updateExtractDialogOnComplete(
    viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
    jobId: Long,
    message: String,
) {
    viewModelStateFlow.update { state ->
        val dialog = state.extractDialog ?: return@update state
        if (dialog.jobId != jobId && dialog.jobId != null) {
            return@update state
        }
        state.copy(
            extractDialog = dialog.copy(
                jobId = jobId,
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
        if (dialog.jobId != jobId && dialog.jobId != null) {
            return@update state
        }
        state.copy(
            extractDialog = dialog.copy(
                jobId = jobId,
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
    callbacks: ExtractDialogUiState.Callbacks,
): ExtractDialogUiState {
    val resultButtonsVisible = dialog.isExtracting || dialog.isExtractComplete
    return ExtractDialogUiState(
        defaultName = dialog.folderName,
        mode = dialog.mode,
        hintMessage = null,
        nameInputVisible = !resultButtonsVisible,
        progressVisible = dialog.isExtracting,
        progress = progress?.progress,
        progressText = progress?.progressText,
        statusMessage = dialog.statusMessage,
        statusMessageLinkText = dialog.jobId?.let {
            ExtractOutputLocationResolver.parseDuplicateOutputName(dialog.statusMessage)
        },
        deleteSourceCheckboxVisible = dialog.isExtractComplete,
        resultButtonsVisible = resultButtonsVisible,
        resultButtonsEnabled = true,
        openButtonEnabled = dialog.isExtractComplete,
        callbacks = callbacks,
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
