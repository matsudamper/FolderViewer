package net.matsudamper.folderviewer.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.matsudamper.folderviewer.ui.browser.ActionMode

internal data class GlobalActionState(
    val actionMode: ActionMode = ActionMode.None,
    val sourceViewerPageIndex: Int? = null,
)

internal class GlobalActionStateStore {
    private val mutableState: MutableStateFlow<GlobalActionState> = MutableStateFlow(GlobalActionState())
    val state: StateFlow<GlobalActionState> = mutableState.asStateFlow()

    fun setActionMode(
        actionMode: ActionMode,
        sourceViewerPageIndex: Int,
    ) {
        mutableState.value = GlobalActionState(
            actionMode = actionMode,
            sourceViewerPageIndex = sourceViewerPageIndex,
        )
    }

    fun clearActionMode() {
        mutableState.value = GlobalActionState()
    }
}
