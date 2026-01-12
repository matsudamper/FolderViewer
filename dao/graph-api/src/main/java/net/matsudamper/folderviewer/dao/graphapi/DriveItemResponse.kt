package net.matsudamper.folderviewer.dao.graphapi

import kotlinx.serialization.Serializable

@Serializable
data class DriveItemCollectionResponse(
    val value: List<DriveItemResponse>,
)

@Serializable
data class DriveItemResponse(
    val id: String,
    val name: String,
    val folder: FolderFacet? = null,
    val size: Long? = null,
    val lastModifiedDateTime: String? = null,
)

@Serializable
data class FolderFacet(
    val childCount: Int? = null,
)
