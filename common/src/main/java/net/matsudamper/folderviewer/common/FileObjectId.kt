package net.matsudamper.folderviewer.common

import kotlinx.serialization.Serializable

@Serializable
sealed interface FileObjectId {
    @Serializable
    data object Root : FileObjectId

    @Serializable
    data class Item(val id: String) : FileObjectId
}
