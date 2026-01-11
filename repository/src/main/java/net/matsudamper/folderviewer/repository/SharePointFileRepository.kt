package net.matsudamper.folderviewer.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.azure.identity.ClientSecretCredentialBuilder
import com.microsoft.graph.serviceclient.GraphServiceClient

/**
 * SharePoint用のFileRepository実装
 */
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

    override suspend fun getFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val driveId = getDriveId() ?: return@withContext emptyList()
        val itemId = resolveItemIdByPath(driveId, path) ?: return@withContext emptyList()
        val driveItems = fetchDriveItems(driveId, itemId) ?: return@withContext emptyList()

        mapToFileItems(driveItems.value, path)
    }

    private fun getDriveId(): String? {
        val drive = try {
            graphServiceClient.users().byUserId(config.objectId).drive().get()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null

        return drive.id
    }

    private fun resolveItemIdByPath(driveId: String, path: String): String? {
        if (path.isEmpty()) {
            return "root"
        }

        val pathParts = path.split("/")
        var currentItemId = "root"

        for (part in pathParts) {
            val items = try {
                graphServiceClient.drives().byDriveId(driveId).items()
                    .byDriveItemId(currentItemId).children().get()
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }

            val nextItem = items.value?.find { it.name == part } ?: return null
            currentItemId = nextItem.id ?: return null
        }

        return currentItemId
    }

    private fun fetchDriveItems(driveId: String, itemId: String) = try {
        graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(itemId).children().get()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun mapToFileItems(driveItems: List<com.microsoft.graph.models.DriveItem>?, path: String): List<FileItem> {
        return driveItems?.mapNotNull { item ->
            val itemName = item.name ?: return@mapNotNull null
            val itemPath = if (path.isEmpty()) {
                itemName
            } else {
                "$path/$itemName"
            }

            FileItem(
                name = itemName,
                path = itemPath,
                isDirectory = item.folder != null,
                size = item.size ?: 0L,
                lastModified = item.lastModifiedDateTime?.toInstant()?.toEpochMilli() ?: 0L,
            )
        }?.sortedWith(
            compareBy<FileItem> { !it.isDirectory }
                .thenBy { it.name.lowercase() },
        ).orEmpty()
    }

    override suspend fun getFileContent(path: String): InputStream = withContext(Dispatchers.IO) {
        // TODO: Microsoft Graph APIを使用してファイルコンテンツを取得
        // 例: GET https://graph.microsoft.com/v1.0/sites/{site-id}/drive/items/{item-id}/content
        ByteArrayInputStream(ByteArray(0))
    }

    override suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream = withContext(Dispatchers.IO) {
        // TODO: Microsoft Graph APIを使用してサムネイルを取得
        // 例: GET https://graph.microsoft.com/v1.0/sites/{site-id}/drive/items/{item-id}/thumbnails
        getFileContent(path)
    }

    override suspend fun uploadFile(destinationPath: String, fileName: String, inputStream: InputStream) {
        TODO("Not yet implemented")
    }

    override suspend fun uploadFolder(destinationPath: String, folderName: String, files: List<FileToUpload>) {
        TODO("Not yet implemented")
    }
}
