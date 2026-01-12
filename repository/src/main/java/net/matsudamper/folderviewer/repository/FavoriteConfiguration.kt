package net.matsudamper.folderviewer.repository

import net.matsudamper.folderviewer.common.FileObjectId

data class FavoriteConfiguration(
    val id: String,
    val name: String,
    val storageId: String,
    val path: String,
    val fileId: FileObjectId.Item,
    val displayPath: String,
)
