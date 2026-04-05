package net.matsudamper.folderviewer.common

import kotlinx.serialization.Serializable

@Serializable
sealed interface FileObjectId {
    val storageId: StorageId

    @Serializable
    data class Root(override val storageId: StorageId) : FileObjectId

    @Serializable
    data class Item(override val storageId: StorageId, val id: String) : FileObjectId
}
