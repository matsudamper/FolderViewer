package net.matsudamper.folderviewer.viewmodel.util

internal enum class CompressArchiveKind {
    Zip,
    TarXz,
    TarZst,
    Zst,
    Xz,
}

internal object CompressArchiveFileNameUtil {
    fun normalizeFileName(fileName: String): String {
        if (!fileName.endsWith(".apk", ignoreCase = true)) {
            return fileName
        }
        val withoutApk = fileName.dropLast(4)
        return if (detectKindOnNormalizedName(withoutApk) != null) {
            withoutApk
        } else {
            fileName
        }
    }

    fun detectKind(fileName: String): CompressArchiveKind? {
        return detectKindOnNormalizedName(normalizeFileName(fileName))
    }

    fun defaultOutputName(fileName: String, kind: CompressArchiveKind): String {
        val normalized = normalizeFileName(fileName)
        return when (kind) {
            CompressArchiveKind.Zip -> ZipFileUtil.zipFileDefaultFolderName(normalized)
            CompressArchiveKind.TarXz -> dropSuffixes(normalized, ".tar.xz", ".txz")
            CompressArchiveKind.TarZst -> normalized.dropLast(".tar.zst".length)
            CompressArchiveKind.Zst -> normalized.dropLast(".zst".length)
            CompressArchiveKind.Xz -> normalized.dropLast(".xz".length)
        }
    }

    private fun detectKindOnNormalizedName(fileName: String): CompressArchiveKind? {
        return when {
            fileName.endsWith(".tar.xz", ignoreCase = true) ||
                fileName.endsWith(".txz", ignoreCase = true) -> CompressArchiveKind.TarXz
            fileName.endsWith(".tar.zst", ignoreCase = true) -> CompressArchiveKind.TarZst
            fileName.endsWith(".zip", ignoreCase = true) -> CompressArchiveKind.Zip
            fileName.endsWith(".zst", ignoreCase = true) -> CompressArchiveKind.Zst
            fileName.endsWith(".xz", ignoreCase = true) -> CompressArchiveKind.Xz
            else -> null
        }
    }

    private fun dropSuffixes(fileName: String, vararg suffixes: String): String {
        val matched = suffixes.firstOrNull { fileName.endsWith(it, ignoreCase = true) }
            ?: return fileName
        return fileName.dropLast(matched.length)
    }
}
