package net.matsudamper.folderviewer.ui.storage

public data class SharePointInput(
    val name: String,
    val objectId: String,
    val tenantId: String,
    val clientId: String,
    val clientSecret: String,
)
