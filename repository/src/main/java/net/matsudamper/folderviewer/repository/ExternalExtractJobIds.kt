package net.matsudamper.folderviewer.repository

import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.common.StorageId

internal object ExternalExtractJobIds {
    val storageId = StorageId(id = "external-extract")
    val sourceFileObjectId = FileObjectId.Item(storageId = storageId, id = "source")
    val parentFileObjectId = FileObjectId.Root(storageId = storageId)
}
