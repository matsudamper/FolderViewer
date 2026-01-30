package net.matsudamper.folderviewer.repository

import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId

data class FavoriteConfiguration(
    val id: String,
    val name: String,
    val storageId: StorageId,
    val path: String,
    val fileId: FileObjectId.Item,
    val displayPath: String,
)
