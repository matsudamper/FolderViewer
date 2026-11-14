package net.matsudamper.folderviewer.viewmodel.browser

import kotlinx.coroutines.CancellationException
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
                inputEnabled = !it.isRenaming,
                confirmButtonEnabled = !it.isRenaming && it.canConfirm(),
                closeEnabled = !it.isRenaming,
                visibleRenamingProgress = it.isRenaming,
            )
        }
    }

    fun open(target: FileItem) {
        dialogStateFlow.value = DialogState(
            target = target,
            input = target.displayPath,
            isRenaming = false,
        )
    }

    fun updateInput(input: String) {
        dialogStateFlow.update { state ->
            if (state == null || state.isRenaming) state else state.copy(input = input)
        }
    }

    fun dismiss() {
        dialogStateFlow.update { state -> if (state?.isRenaming == true) state else null }
    }

    /**
     * 名前変更が終わるまでダイアログを開いたままにする。
     * 成功したらダイアログを閉じて [RenameResult.Success] を返し、失敗したらダイアログを開いたまま [RenameResult.Failure] を返す。
     */
    suspend fun confirm(rename: suspend (target: FileItem, newName: String) -> Unit): RenameResult? {
        val state = dialogStateFlow.value ?: return null
        if (state.isRenaming || !state.canConfirm()) return null
        dialogStateFlow.value = state.copy(isRenaming = true)
        return try {
            rename(state.target, state.input)
            dialogStateFlow.value = null
            RenameResult.Success(newName = state.input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            dialogStateFlow.value = state.copy(isRenaming = false)
            RenameResult.Failure
        }
    }

    sealed interface RenameResult {
        data class Success(val newName: String) : RenameResult
        data object Failure : RenameResult
    }

    private data class DialogState(
        val target: FileItem,
        val input: String,
        val isRenaming: Boolean,
    ) {
        fun canConfirm(): Boolean = input != target.displayPath && isValidFileName(input)
    }
}

internal fun isValidFileName(name: String): Boolean {
    return name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        name.none { it == '/' || it == '\\' || it == '\u0000' }
}
