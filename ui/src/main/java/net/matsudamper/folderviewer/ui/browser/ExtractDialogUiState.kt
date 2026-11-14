package net.matsudamper.folderviewer.ui.browser

import androidx.compose.runtime.Immutable

@Immutable
data class ExtractDialogUiState(
    val defaultName: String,
    val mode: ExtractDialogMode,
    val hintMessage: String?,
    val nameInputVisible: Boolean,
    val progressVisible: Boolean,
    val progress: Float?,
    val progressText: String?,
    val statusMessage: String?,
    val statusMessageLinkText: String?,
    val deleteSourceCheckboxVisible: Boolean,
    val resultButtonsVisible: Boolean,
    val resultButtonsEnabled: Boolean,
    val openButtonEnabled: Boolean,
    val callbacks: Callbacks,
) {
    @Immutable
    interface Callbacks {
        fun onDismissRequest()
        fun onClose()
        fun onConfirm(outputName: String)
        fun onOpenResult()
        fun onOpenDetail()
        fun onDeleteSourceRequested()
        fun onStatusMessageLinkClick()
    }
}
