package net.matsudamper.folderviewer.viewmodel.browser

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.viewmodel.util.FileUtil

internal object FileBrowserFileOpener {
    fun open(
        fileItem: FileItem,
        sortedFiles: List<FileItem>,
        displayName: String,
        context: FileBrowserExtractDialogPresenter.OpenNonMediaFileContext,
        sendEvent: suspend (FileBrowserViewModel.ViewModelEvent) -> Unit,
    ) {
        if (fileItem.isDirectory) {
            context.viewModelScope.launch {
                sendEvent(
                    FileBrowserViewModel.ViewModelEvent.NavigateToFileBrowser(
                        displayPath = "$displayName/${fileItem.displayPath}",
                        id = fileItem.id,
                    ),
                )
            }
            return
        }
        val isImage = FileUtil.isImage(fileItem.displayPath.lowercase())
        val isVideo = FileUtil.isVideo(fileItem.displayPath.lowercase())
        context.viewModelStateFlow.update { it.copy(lastOpenedFileKey = fileItem.id.id) }
        when {
            isImage -> {
                context.viewModelScope.launch {
                    sendEvent(
                        FileBrowserViewModel.ViewModelEvent.NavigateToImageViewer(
                            id = fileItem.id,
                            allPaths = sortedFiles.filter { FileUtil.isImage(it.displayPath) }.map { it.id },
                        ),
                    )
                }
            }

            isVideo -> {
                context.viewModelScope.launch {
                    context.openWithExternalPlayer(fileItem)
                }
            }

            else -> FileBrowserExtractDialogPresenter.openNonMediaFile(
                fileItem = fileItem,
                context = context,
            )
        }
    }
}
