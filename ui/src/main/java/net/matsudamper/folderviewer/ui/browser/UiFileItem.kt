package net.matsudamper.folderviewer.ui.browser

public data class UiFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
