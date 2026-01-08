package net.matsudamper.folderviewer.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
object Home : NavKey

@Serializable
object Settings : NavKey

@Serializable
object StorageTypeSelection : NavKey

@Serializable
object PermissionRequest : NavKey

@Serializable
data class SmbAdd(val storageId: String? = null) : NavKey

@Serializable
data class FileBrowser(val storageId: String, val path: String?) : NavKey

@Serializable
data class FolderBrowser(val storageId: String, val path: String) : NavKey

@Serializable
data class ImageViewer(val id: String, val path: String, val allPaths: List<String>) : NavKey
