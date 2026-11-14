package net.matsudamper.folderviewer.viewmodel.browser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState

internal class FileBrowserRenameDialogController {
    private val dialogStateFlow = MutableStateFlow<DialogState?>(null)

    val dialogUiState: Flow<FileBrowserUiState.RenameDialog?> = dialogStateFlow.map { state ->
        state?.let {
            FileBrowserUiState.RenameDialog(
                input = it.input,
                confirmButtonEnabled = it.input != it.target.displayPath && isValidFileName(it.input),
            )
        }
    }

    fun open(target: FileItem) {
        dialogStateFlow.value = DialogState(target = target, input = target.displayPath)
    }

    fun updateInput(input: String) {
        dialogStateFlow.update { it?.copy(input = input) }
    }

    fun dismiss() {
        dialogStateFlow.value = null
    }

    fun takeConfirmedRequest(): ConfirmedRequest? {
        val state = dialogStateFlow.value ?: return null
        if (state.input == state.target.displayPath || !isValidFileName(state.input)) return null
        dialogStateFlow.value = null
        return ConfirmedRequest(target = state.target, newName = state.input)
    }

    data class ConfirmedRequest(
        val target: FileItem,
        val newName: String,
    )

    private data class DialogState(
        val target: FileItem,
        val input: String,
    )
}
