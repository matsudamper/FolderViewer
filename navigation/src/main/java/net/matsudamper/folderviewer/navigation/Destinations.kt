package net.matsudamper.folderviewer.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId

@Serializable
object Home : NavKey

@Serializable
object Settings : NavKey

@Serializable
object StorageTypeSelection : NavKey

@Serializable
object PermissionRequest : NavKey

@Serializable
data class SmbAdd(val storageId: StorageId? = null) : NavKey

@Serializable
data class SharePointAdd(val storageId: StorageId? = null) : NavKey

@Serializable
data class FileBrowser(val displayPath: String?, val fileId: FileObjectId) : NavKey

@Serializable
data class FolderBrowser(val displayPath: String?, val fileId: FileObjectId) : NavKey

@Serializable
data class ImageViewer(val fileId: FileObjectId.Item, val allPaths: List<FileObjectId.Item>) : NavKey

@Serializable
object UploadProgress : NavKey

@Serializable
data class UploadDetail(val workerId: String) : NavKey
