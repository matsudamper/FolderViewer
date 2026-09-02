package net.matsudamper.folderviewer.viewmodel.util

import net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType

internal object ExtractableFileNameUtil {
    fun detect(fileName: String): ExtractableFileType? {
        return when (val kind = CompressArchiveFileNameUtil.detectKind(fileName)) {
            CompressArchiveKind.Zip -> ExtractableFileType.Zip
            CompressArchiveKind.TarXz -> ExtractableFileType.TarXz
            CompressArchiveKind.TarZst -> ExtractableFileType.TarZst
            CompressArchiveKind.Zst -> ExtractableFileType.Compressed(CompressedFileUtil.Format.Zst)
            CompressArchiveKind.Xz -> ExtractableFileType.Compressed(CompressedFileUtil.Format.Xz)
            null -> null
        }
    }

    fun defaultOutputName(fileName: String, type: ExtractableFileType): String {
        val kind = type.toKind()
        return CompressArchiveFileNameUtil.defaultOutputName(fileName, kind)
    }

    private fun ExtractableFileType.toKind(): CompressArchiveKind {
        return when (this) {
            ExtractableFileType.Zip -> CompressArchiveKind.Zip
            ExtractableFileType.TarXz -> CompressArchiveKind.TarXz
            ExtractableFileType.TarZst -> CompressArchiveKind.TarZst
            is ExtractableFileType.Compressed -> when (format) {
                CompressedFileUtil.Format.Zst -> CompressArchiveKind.Zst
                CompressedFileUtil.Format.Xz -> CompressArchiveKind.Xz
            }
        }
    }
}
