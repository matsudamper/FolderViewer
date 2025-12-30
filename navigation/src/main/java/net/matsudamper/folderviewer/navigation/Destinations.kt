package net.matsudamper.folderviewer.navigation

import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
object Settings

@Serializable
object StorageTypeSelection

@Serializable
object SmbAdd

@Serializable
data class FileBrowser(val id: String)
