package net.matsudamper.folderviewer.navigation

import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
object Settings

@Serializable
object StorageTypeSelection

@Serializable
data class SmbAdd(val storageId: String? = null)

@Serializable
data class FileBrowser(val storageId: String)

@Serializable
data class ImageViewer(val id: String, val path: String)
