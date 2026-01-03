package net.matsudamper.folderviewer.viewmodel

object FileUtil {
    fun isImage(name: String): Boolean {
        val lowerName = name.lowercase()
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") || lowerName.endsWith(".bmp") ||
            lowerName.endsWith(".gif") || lowerName.endsWith(".webp")
    }
}
