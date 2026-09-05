package net.matsudamper.folderviewer.viewmodel.extract

import android.net.Uri

sealed interface ExternalIncomingUriResolution {
    data class Extractable(val args: ExternalExtractLaunchArgs) : ExternalIncomingUriResolution

    data class Directory(
        val uri: Uri,
        val mimeType: String,
    ) : ExternalIncomingUriResolution

    data object Unsupported : ExternalIncomingUriResolution

    data class Failed(
        val message: String,
    ) : ExternalIncomingUriResolution
}
