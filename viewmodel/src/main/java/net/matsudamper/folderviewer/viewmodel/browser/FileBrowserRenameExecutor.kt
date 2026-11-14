package net.matsudamper.folderviewer.viewmodel.browser

import kotlinx.coroutines.CancellationException
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository

internal suspend fun executeRename(
    repository: FileRepository,
    fileItem: FileItem,
    newName: String,
    refreshFiles: suspend () -> Unit,
    sendSnackbar: suspend (String) -> Unit,
) {
    runCatching {
        repository.rename(fileItem.id, newName)
        refreshFiles()
        sendSnackbar("${newName}に名前を変更しました")
    }.onFailure { e ->
        when (e) {
            is CancellationException -> throw e

            else -> {
                e.printStackTrace()
                sendSnackbar("名前の変更に失敗しました")
            }
        }
    }
}
