package net.matsudamper.folderviewer.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.time.OffsetDateTime
import kotlin.collections.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.azure.identity.ClientSecretCredentialBuilder
import com.microsoft.graph.models.DriveItem
import com.microsoft.graph.models.File
import com.microsoft.graph.serviceclient.GraphServiceClient
import net.matsudamper.folderviewer.dao.graphapi.GraphApiClient

class SharePointFileRepository(
    private val config: StorageConfiguration.SharePoint,
) : FileRepository {
    private val graphServiceClient: GraphServiceClient by lazy {
        val credential = ClientSecretCredentialBuilder()
            .clientId(config.clientId)
            .clientSecret(config.clientSecret)
            .tenantId(config.tenantId)
            .build()

        GraphServiceClient(credential, "https://graph.microsoft.com/.default")
    }

    private val graphApiClient: GraphApiClient by lazy {
        GraphApiClient(
            tenantId = config.tenantId,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            objectId = config.objectId,
        )
    }

    override suspend fun getFiles(id: String?): List<FileItem> {
        return withContext(Dispatchers.IO) {
            val driveItems = graphApiClient.getDriveItemChildren(
                itemId = id,
            )

            driveItems.value.map { item ->
                val itemName = item.name

                FileItem(
                    displayName = itemName,
                    id = item.id,
                    isDirectory = item.folder != null,
                    size = item.size ?: 0L,
                    lastModified = item.lastModifiedDateTime?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L,
                )
            }.sortedWith(
                compareBy<FileItem> { !it.isDirectory }
                    .thenBy { it.displayName.lowercase() },
            )
        }
    }

    private suspend fun getDriveId(): String {
        val drive = graphApiClient.getDrive(config.objectId)
        return drive.id
    }

    private suspend fun resolveItemIdByPath(path: String): String? {
        if (path.isEmpty()) {
            return "root"
        }

        val pathParts = path.split("/")
        var currentItemId = "root"

        for (part in pathParts) {
            val items = graphApiClient.getDriveItemChildren(
                itemId = currentItemId,
            )

            val nextItem = items.value.find { it.name == part } ?: return null
            currentItemId = nextItem.id
        }

        return currentItemId
    }

    override suspend fun getFileContent(path: String): InputStream = withContext(Dispatchers.IO) {
        val driveId = getDriveId()
        val itemId = resolveItemIdByPath(path) ?: return@withContext ByteArrayInputStream(ByteArray(0))

        graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(itemId).content().get()
            ?: ByteArrayInputStream(ByteArray(0))
    }

    override suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream? {
        return withContext(Dispatchers.IO) {
            val driveId = getDriveId()
            val itemId = resolveItemIdByPath(path) ?: return@withContext ByteArrayInputStream(ByteArray(0))

            val thumbnails = graphServiceClient.drives().byDriveId(driveId)
                .items().byDriveItemId(itemId)
                .thumbnails().get()

            val thumbnail = thumbnails?.value?.firstOrNull()
            val thumbnailUrl = when {
                thumbnailSize <= 256 -> thumbnail?.small?.url
                thumbnailSize <= 512 -> thumbnail?.medium?.url
                else -> thumbnail?.large?.url
            } ?: return@withContext null

            return@withContext URL(thumbnailUrl).openStream()
        }
    }

    override suspend fun uploadFile(
        id: String?,
        fileName: String,
        inputStream: InputStream,
    ) {
        id ?: return // TODO rootへのアップロードを対応
        withContext(Dispatchers.IO) {
            val driveId = getDriveId()
            val parentItemId = resolveItemIdByPath(id)
                ?: throw IllegalArgumentException("Destination path not found: $id")

            val bytes = inputStream.readBytes()
            val byteStream = ByteArrayInputStream(bytes)

            val driveItem = DriveItem().also { item ->
                item.name = fileName
                item.file = File()
            }

            val newItem = graphServiceClient.drives().byDriveId(driveId)
                .items().byDriveItemId(parentItemId)
                .children()
                .post(driveItem) ?: throw IllegalStateException("Failed to create item")

            val itemId = newItem.id ?: throw IllegalStateException("Item ID is null")
            graphServiceClient.drives().byDriveId(driveId)
                .items().byDriveItemId(itemId)
                .content()
                .put(byteStream)
        }
    }

    override suspend fun uploadFolder(
        id: String?,
        folderName: String,
        files: List<FileToUpload>,
    ) {
        id ?: return // TODO rootへのアップロードを対応
        withContext(Dispatchers.IO) {
            val driveId = getDriveId()
            val parentItemId = resolveItemIdByPath(id)
                ?: throw IllegalArgumentException("Destination path not found: $id")

            val folderItem = DriveItem().also { item ->
                item.name = folderName
                item.folder = com.microsoft.graph.models.Folder()
            }

            val createdFolder = graphServiceClient.drives().byDriveId(driveId)
                .items().byDriveItemId(parentItemId)
                .children()
                .post(folderItem) ?: throw IllegalStateException("Failed to create folder")

            val folderId = createdFolder.id ?: throw IllegalStateException("Folder ID is null")

            files.forEach { fileToUpload ->
                val pathParts = fileToUpload.relativePath.split("/")
                val fileName = pathParts.last()
                val directories = pathParts.dropLast(1)

                var currentParentId = folderId
                directories.forEach { dirName ->
                    currentParentId = createOrGetFolder(driveId, currentParentId, dirName)
                }

                val bytes = fileToUpload.inputStream.readBytes()
                val byteStream = ByteArrayInputStream(bytes)

                val fileItem = DriveItem().also { item ->
                    item.name = fileName
                    item.file = File()
                }

                val newItem = graphServiceClient.drives().byDriveId(driveId)
                    .items().byDriveItemId(currentParentId)
                    .children()
                    .post(fileItem) ?: throw IllegalStateException("Failed to create item")

                val itemId = newItem.id ?: throw IllegalStateException("Item ID is null")
                graphServiceClient.drives().byDriveId(driveId)
                    .items().byDriveItemId(itemId)
                    .content()
                    .put(byteStream)
            }
        }
    }

    private fun createOrGetFolder(driveId: String, parentId: String, folderName: String): String {
        val existingItems = graphServiceClient.drives().byDriveId(driveId)
            .items().byDriveItemId(parentId)
            .children().get()

        val existingFolder = existingItems?.value?.find { it.name == folderName && it.folder != null }
        if (existingFolder != null) {
            return requireNotNull(existingFolder.id) { "Existing folder ID is null" }
        }

        val folderItem = DriveItem().also { item ->
            item.name = folderName
            item.folder = com.microsoft.graph.models.Folder()
        }

        val createdFolder = graphServiceClient.drives().byDriveId(driveId)
            .items().byDriveItemId(parentId)
            .children()
            .post(folderItem)

        return requireNotNull(createdFolder?.id) { "Failed to create folder or folder ID is null" }
    }
}
