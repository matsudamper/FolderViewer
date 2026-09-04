package net.matsudamper.folderviewer.viewmodel.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode

internal object FileBrowserExtractDialogPresenter {
    internal data class OpenNonMediaFileContext(
        val localFolderPath: String?,
        val zipHandler: FileBrowserZipHandler,
        val viewModelScope: CoroutineScope,
        val pendingExtractFileItemSetter: (FileItem?) -> Unit,
        val viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
        val openWithExternalPlayer: suspend (FileItem) -> Unit,
    )

    fun showExtractDialog(
        fileItem: FileItem,
        zipHandler: FileBrowserZipHandler,
        pendingExtractFileItemSetter: (FileItem?) -> Unit,
        viewModelStateFlow: MutableStateFlow<FileBrowserViewModel.ViewModelState>,
    ) {
        val input = zipHandler.createExtractDialogInput(fileItem) ?: return
        pendingExtractFileItemSetter(input.fileItem)
        viewModelStateFlow.update {
            it.copy(
                extractDialog = FileBrowserViewModel.ViewModelState.ExtractDialogState(
                    folderName = input.outputName,
                    isExtracting = false,
                    isExtractComplete = false,
                    statusMessage = null,
                    jobId = null,
                    mode = input.mode,
                ),
            )
        }
    }

    fun dialogModeForConfirm(
        fileItem: FileItem,
        zipHandler: FileBrowserZipHandler,
        currentMode: ExtractDialogMode?,
    ): ExtractDialogMode {
        return currentMode ?: zipHandler.createExtractDialogInput(fileItem)?.mode ?: ExtractDialogMode.ZipFolder
    }

    fun openNonMediaFile(
        fileItem: FileItem,
        context: OpenNonMediaFileContext,
    ) {
        if (context.localFolderPath != null && context.zipHandler.getExtractableFileType(fileItem) != null) {
            showExtractDialog(
                fileItem = fileItem,
                zipHandler = context.zipHandler,
                pendingExtractFileItemSetter = context.pendingExtractFileItemSetter,
                viewModelStateFlow = context.viewModelStateFlow,
            )
            return
        }
        context.viewModelScope.launch {
            context.openWithExternalPlayer(fileItem)
        }
    }
}
