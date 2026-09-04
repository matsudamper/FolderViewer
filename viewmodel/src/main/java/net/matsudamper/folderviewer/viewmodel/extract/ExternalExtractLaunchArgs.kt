package net.matsudamper.folderviewer.viewmodel.extract

data class ExternalExtractLaunchArgs(
    val sourcePath: String,
    val outputParentPath: String,
    val fileName: String,
    val extractType: ExtractLaunchType,
    val locationMessage: String?,
)

enum class ExtractLaunchType {
    Zip,
    TarGz,
    TarXz,
    TarZst,
    Zst,
    Xz,
}

internal object ExternalExtractLaunchArgsMapper {
    fun fromResolved(resolved: net.matsudamper.folderviewer.viewmodel.util.ExternalExtractPathResolver.ResolvedExtractFile): ExternalExtractLaunchArgs {
        val extractType = net.matsudamper.folderviewer.viewmodel.util.ExtractableFileNameUtil.detect(resolved.fileName)
            ?: error("unsupported file")
        val locationMessage = if (resolved.usedFallbackOutputLocation) {
            "Documents/FolderViewerに展開します"
        } else {
            null
        }
        return ExternalExtractLaunchArgs(
            sourcePath = resolved.sourceFile.absolutePath,
            outputParentPath = resolved.outputParentPath,
            fileName = resolved.fileName,
            extractType = extractType.toLaunchType(),
            locationMessage = locationMessage,
        )
    }

    private fun net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.toLaunchType(): ExtractLaunchType {
        return when (this) {
            net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.Zip -> ExtractLaunchType.Zip

            net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.TarGz -> ExtractLaunchType.TarGz

            net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.TarXz -> ExtractLaunchType.TarXz

            net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.TarZst -> ExtractLaunchType.TarZst

            is net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType.Compressed -> when (format) {
                net.matsudamper.folderviewer.viewmodel.util.CompressedFileUtil.Format.Zst -> ExtractLaunchType.Zst
                net.matsudamper.folderviewer.viewmodel.util.CompressedFileUtil.Format.Xz -> ExtractLaunchType.Xz
            }
        }
    }
}
