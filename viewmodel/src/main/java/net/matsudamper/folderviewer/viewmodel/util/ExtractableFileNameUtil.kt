package net.matsudamper.folderviewer.viewmodel.util

import net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType

internal object ExtractableFileNameUtil {
    fun detect(fileName: String): ExtractableFileType? {
        if (fileName.endsWith(".zip", ignoreCase = true)) {
            return ExtractableFileType.Zip
        }
        val compressedFormat = CompressedFileUtil.detectFormat(fileName) ?: return null
        return ExtractableFileType.Compressed(compressedFormat)
    }

    fun defaultOutputName(fileName: String, type: ExtractableFileType): String {
        return when (type) {
            ExtractableFileType.Zip -> ZipFileUtil.zipFileDefaultFolderName(fileName)
            is ExtractableFileType.Compressed -> CompressedFileUtil.defaultOutputName(
                fileName = fileName,
                format = type.format,
            )
        }
    }
}
