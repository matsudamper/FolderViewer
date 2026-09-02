package net.matsudamper.folderviewer.viewmodel.extract

import android.content.Context
import android.net.Uri
import net.matsudamper.folderviewer.viewmodel.util.ExternalExtractPathResolver

object ExternalExtractIntentHandler {
    fun resolveLaunchArgs(context: Context, uri: Uri): ExternalExtractLaunchArgs? {
        val resolved = ExternalExtractPathResolver.resolve(context, uri) ?: return null
        return ExternalExtractLaunchArgsMapper.fromResolved(resolved)
    }
}
