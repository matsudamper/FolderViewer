package net.matsudamper.folderviewer.repository

data class FavoriteConfiguration(
    val id: String,
    val name: String,
    val storageId: String,
    val path: String,
)
