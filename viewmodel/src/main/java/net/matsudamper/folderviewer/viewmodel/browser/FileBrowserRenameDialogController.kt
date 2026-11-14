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
                errorMessage = it.errorMessage,
            )
        }
    }

    fun open(target: FileItem) {
        dialogStateFlow.value = DialogState(
            target = target,
            input = target.displayPath,
            isRenaming = false,
            errorMessage = null,
        )
    }

    fun updateInput(input: String) {
        dialogStateFlow.update { state ->
            if (state == null || state.isRenaming) state else state.copy(input = input, errorMessage = null)
        }
    }

    fun dismiss() {
        dialogStateFlow.update { state -> if (state?.isRenaming == true) state else null }
    }

    /**
     * 名前変更が終わるまでダイアログを開いたままにする。
     * 成功したらダイアログを閉じて新しい名前を返し、失敗したらエラーを表示したまま null を返す。
     */
    suspend fun confirm(rename: suspend (target: FileItem, newName: String) -> Unit): String? {
        val state = dialogStateFlow.value ?: return null
        if (state.isRenaming || !state.canConfirm()) return null
        dialogStateFlow.value = state.copy(isRenaming = true, errorMessage = null)
        return try {
            rename(state.target, state.input)
            dialogStateFlow.value = null
            state.input
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            dialogStateFlow.value = state.copy(isRenaming = false, errorMessage = "名前の変更に失敗しました")
            null
        }
    }

    private data class DialogState(
        val target: FileItem,
        val input: String,
        val isRenaming: Boolean,
        val errorMessage: String?,
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
