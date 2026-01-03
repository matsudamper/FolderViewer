package net.matsudamper.folderviewer.viewmodel

object FileUtil {
    fun isImage(name: String): Boolean {
        val name = name.lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".png") || name.endsWith(".bmp") ||
            name.endsWith(".gif") || name.endsWith(".webp")
    }
}
