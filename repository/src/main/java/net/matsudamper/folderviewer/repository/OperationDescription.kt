package net.matsudamper.folderviewer.repository

internal object OperationDescription {
    fun build(files: List<File>, fallback: String): String {
        val directory = files.filter { it.isDirectory }.minByOrNull { pathDepth(it.path) }
        if (directory != null) return directory.path

        val normalFiles = files.filterNot { it.isDirectory }
        val first = normalFiles.minByOrNull { pathDepth(it.path) } ?: return fallback
        return if (normalFiles.size > 1) "${first.path} 他" else first.path
    }

    fun joinPath(relativePath: String, fileName: String): String {
        return if (relativePath.isEmpty()) fileName else "$relativePath/$fileName"
    }

    private fun pathDepth(path: String): Int {
        return path.count { it == '/' }
    }

    data class File(
        val path: String,
        val isDirectory: Boolean,
    )
}
