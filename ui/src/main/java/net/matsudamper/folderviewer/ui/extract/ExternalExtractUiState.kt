package net.matsudamper.folderviewer.ui.extract

import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode

data class ExternalExtractUiState(
    val defaultName: String,
    val mode: ExtractDialogMode,
    val isExtracting: Boolean,
    val isExtractComplete: Boolean,
    val statusMessage: String?,
    val locationMessage: String?,
    val progress: Float? = null,
    val progressText: String? = null,
    val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onDismissRequest()

        fun onConfirm(outputName: String)

        fun onOpenResult()

        fun onOpenDetail()
    }
}
