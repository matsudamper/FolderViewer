package net.matsudamper.folderviewer.viewmodel.extract

import android.content.Context
import android.net.Uri
import net.matsudamper.folderviewer.viewmodel.util.ExternalDirectoryUriInspector
import net.matsudamper.folderviewer.viewmodel.util.ExternalExtractPathResolver

object ExternalExtractIntentHandler {
    fun resolve(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
    ): ExternalIncomingUriResolution {
        if (uri.scheme == "file") {
            return if (ExternalDirectoryUriInspector.isDirectory(context, uri)) {
                ExternalIncomingUriResolution.Unsupported
            } else {
                resolveExtractableOrUnsupported(context, uri)
            }
        }
        if (ExternalDirectoryUriInspector.isDirectory(context, uri)) {
            return ExternalIncomingUriResolution.Directory(
                uri = uri,
                mimeType = ExternalDirectoryUriInspector.resolveDirectoryMimeType(
                    context = context,
                    uri = uri,
                    intentMimeType = mimeType,
                ),
            )
        }
        return resolveExtractableOrUnsupported(context, uri)
    }

    private fun resolveExtractableOrUnsupported(
        context: Context,
        uri: Uri,
    ): ExternalIncomingUriResolution {
        val launchArgs = resolveLaunchArgs(context, uri)
        return if (launchArgs == null) {
            ExternalIncomingUriResolution.Unsupported
        } else {
            ExternalIncomingUriResolution.Extractable(launchArgs)
        }
    }

    fun resolveLaunchArgs(context: Context, uri: Uri): ExternalExtractLaunchArgs? {
        val resolved = ExternalExtractPathResolver.resolve(context, uri) ?: return null
        return ExternalExtractLaunchArgsMapper.fromResolved(resolved)
    }
}
