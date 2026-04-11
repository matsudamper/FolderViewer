package net.matsudamper.folderviewer.repository

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import kotlin.collections.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.azure.identity.ClientSecretCredentialBuilder
import com.microsoft.graph.core.tasks.LargeFileUploadTask
import com.microsoft.graph.drives.item.items.item.createuploadsession.CreateUploadSessionPostRequestBody
import com.microsoft.graph.models.DriveItem
import com.microsoft.graph.models.DriveItemUploadableProperties
import com.microsoft.graph.serviceclient.GraphServiceClient
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.dao.graphapi.GraphApiClient

class SharePointFileRepository(
    private val config: StorageConfiguration.SharePoint,
) : RangeReadableFileRepository {
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

    override suspend fun getFiles(id: FileObjectId): List<FileItem> {
        return withContext(Dispatchers.IO) {
            val itemId = when (id) {
                is FileObjectId.Root -> null
                is FileObjectId.Item -> id.id
            }
            val driveItems = graphApiClient.getDriveItemChildren(
                itemId = itemId,
            )

            driveItems.value.map { item ->
                val itemName = item.name

                FileItem(
                    displayPath = itemName,
                    id = FileObjectId.Item(storageId = config.id, id = item.id),
                    isDirectory = item.folder != null,
                    size = item.size ?: 0L,
                    lastModified = item.lastModifiedDateTime?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L,
                )
            }.sortedWith(
                compareBy<FileItem> { !it.isDirectory }
                    .thenBy { it.displayPath.lowercase() },
            )
        }
    }

    private suspend fun getDriveId(): String {
        val drive = graphApiClient.getDrive(config.objectId)
        return drive.id
    }

    private suspend fun getRootItemId(driveId: String): String {
        val rootItem = graphServiceClient.drives().byDriveId(driveId).root().get()
            ?: throw IllegalStateException("Failed to resolve drive root item")
        return requireNotNull(rootItem.id) { "Drive root item id is null" }
    }

    override suspend fun getFileContent(fileId: FileObjectId.Item): InputStream = openFileContent(fileId, offset = 0L)

    override suspend fun openFileContent(fileId: FileObjectId.Item, offset: Long): InputStream = withContext(Dispatchers.IO) {
        require(offset >= 0L) { "offset must be greater than or equal to 0: $offset" }

        val response = graphApiClient.getDriveItemWithDownloadUrl(fileId.id)
        val downloadUrl = response.downloadUrl
            ?: throw IllegalStateException("Download URL not available")
        openDownloadUrl(downloadUrl, offset)
    }

    override suspend fun getFileSize(fileId: FileObjectId.Item): Long = withContext(Dispatchers.IO) {
        val item = graphApiClient.getDriveItem(fileId.id)
        item.size ?: 0L
    }

    override suspend fun getFileInfo(fileId: FileObjectId.Item): FileItem = withContext(Dispatchers.IO) {
        val item = graphApiClient.getDriveItem(fileId.id)
        FileItem(
            id = fileId,
            displayPath = item.name,
            isDirectory = item.folder != null,
            size = item.size ?: 0L,
            lastModified = item.lastModifiedDateTime?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L,
        )
    }

    override suspend fun deleteFile(fileId: FileObjectId.Item): Unit = withContext(Dispatchers.IO) {
        val driveId = getDriveId()
        graphServiceClient.drives().byDriveId(driveId)
            .items().byDriveItemId(fileId.id)
            .delete()
    }

    override suspend fun deleteDirectory(dirId: FileObjectId.Item): Unit = withContext(Dispatchers.IO) {
        val item = graphApiClient.getDriveItem(dirId.id)
        require(item.folder?.childCount == 0) { "ディレクトリが空ではありません: ${dirId.id}" }
        val eTag = requireNotNull(item.eTag) { "eTagが取得できません: ${dirId.id}" }
        graphApiClient.deleteDriveItem(dirId.id, eTag)
    }

    override suspend fun getThumbnail(fileId: FileObjectId.Item, thumbnailSize: Int): InputStream? {
        return withContext(Dispatchers.IO) {
            val driveId = getDriveId()

            val thumbnails = graphServiceClient.drives().byDriveId(driveId)
                .items().byDriveItemId(fileId.id)
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
        id: FileObjectId,
        fileName: String,
        inputStream: InputStream,
        size: Long,
        onRead: FlowCollector<Long>,
        overwrite: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            val driveId = getDriveId()
            val parentId = when (id) {
                is FileObjectId.Root -> getRootItemId(driveId)
                is FileObjectId.Item -> id.id
            }

            coroutineScope {
                val progressInputStream = ProgressInputStream(inputStream)
                val job = launch {
                    progressInputStream.onRead.collect(onRead)
                }

                uploadWithSession(driveId, parentId, fileName, progressInputStream, size, overwrite)

                job.cancel()
            }
        }
    }

    override suspend fun uploadFolder(
        id: FileObjectId,
        folderName: String,
        files: List<FileToUpload>,
        onRead: FlowCollector<UploadProgress>,
    ) {
        withContext(Dispatchers.IO) {
            val driveId = getDriveId()

            val folderItem = DriveItem().also { item ->
                item.name = folderName
                item.folder = com.microsoft.graph.models.Folder()
            }

            val parentId = when (id) {
                is FileObjectId.Root -> getRootItemId(driveId)
                is FileObjectId.Item -> id.id
            }
            val createdFolder = graphServiceClient.drives().byDriveId(driveId)
                .items()
                .byDriveItemId(parentId)
                .children()
                .post(folderItem) ?: throw IllegalStateException("Failed to create folder")

            val folderId = createdFolder.id ?: throw IllegalStateException("Folder ID is null")

            coroutineScope {
                var uploadedSize = 0L
                var completedFiles = 0
                files.forEach { fileToUpload ->
                    val pathParts = fileToUpload.relativePath.split("/")
                    val fileName = pathParts.last()
                    val directories = pathParts.dropLast(1)

                    var currentParentId = folderId
                    directories.forEach { dirName ->
                        currentParentId = createOrGetFolder(driveId, currentParentId, dirName)
                    }

                    val fileSize = requireNotNull(fileToUpload.size) { "File size is required" }

                    val progressInputStream = ProgressInputStream(fileToUpload.inputStream)
                    val job = launch {
                        progressInputStream.onRead.collect { fileReadSize ->
                            onRead.emit(UploadProgress(uploadedSize + fileReadSize, completedFiles))
                        }
                    }

                    uploadWithSession(driveId, currentParentId, fileName, progressInputStream, fileSize)

                    job.cancel()
                    uploadedSize += fileToUpload.size
                    completedFiles++
                }
            }
        }
    }

    private fun uploadWithSession(
        driveId: String,
        parentId: String,
        fileName: String,
        inputStream: InputStream,
        fileSize: Long,
        overwrite: Boolean = false,
    ) {
        val requestBody = CreateUploadSessionPostRequestBody().also { body ->
            body.item = DriveItemUploadableProperties().also { item ->
                item.additionalData = mapOf(
                    "@microsoft.graph.conflictBehavior" to if (overwrite) "replace" else "fail",
                )
            }
        }
        val uploadSession = graphServiceClient.drives().byDriveId(driveId)
            .items().byDriveItemId("$parentId:/$fileName:")
            .createUploadSession()
            .post(requestBody)
            ?: throw IllegalStateException("Failed to create upload session")

        val maxSliceSize = UPLOAD_CHUNK_SIZE
        val task = LargeFileUploadTask<DriveItem>(
            graphServiceClient.requestAdapter,
            uploadSession,
            inputStream,
            fileSize,
            maxSliceSize,
            DriveItem::createFromDiscriminatorValue,
        )
        task.upload()
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

    override suspend fun getViewSourceUri(fileId: FileObjectId.Item): ViewSourceUri =
        withContext(Dispatchers.IO) {
            val response = graphApiClient.getDriveItemWithDownloadUrl(fileId.id)
            val downloadUrl = response.downloadUrl
                ?: throw IllegalStateException("Download URL not available")
            ViewSourceUri.RemoteUrl(downloadUrl)
        }

    override suspend fun createDirectory(
        id: FileObjectId,
        directoryName: String,
    ): FileObjectId.Item {
        return withContext(Dispatchers.IO) {
            val driveId = getDriveId()
            val parentId = when (id) {
                is FileObjectId.Root -> getRootItemId(driveId)
                is FileObjectId.Item -> id.id
            }
            val folderId = createOrGetFolder(driveId, parentId, directoryName)
            FileObjectId.Item(config.id, folderId)
        }
    }

    private fun openDownloadUrl(downloadUrl: String, offset: Long): InputStream {
        val connection = URL(downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MILLIS
        connection.readTimeout = HTTP_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (offset > 0L) {
            connection.setRequestProperty("Range", "bytes=$offset-")
        }

        return try {
            val responseCode = connection.responseCode
            when {
                offset == 0L && (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) -> {
                    DisconnectingInputStream(connection.inputStream, connection)
                }

                offset > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL -> {
                    val contentRange = connection.getHeaderField("Content-Range")
                    if (contentRange?.startsWith("bytes $offset-") != true) {
                        throw IOException("Unexpected Content-Range: $contentRange")
                    }
                    DisconnectingInputStream(connection.inputStream, connection)
                }

                offset > 0L && responseCode == HttpURLConnection.HTTP_OK -> {
                    throw IOException("Range request was not accepted")
                }

                else -> {
                    throw IOException("Failed to open download stream: HTTP $responseCode")
                }
            }
        } catch (e: Throwable) {
            connection.errorStream?.close()
            connection.disconnect()
            throw e
        }
    }

    private class DisconnectingInputStream(
        inputStream: InputStream,
        private val connection: HttpURLConnection,
    ) : FilterInputStream(inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        private const val UPLOAD_CHUNK_SIZE = 5L * 1024 * 1024
        private const val HTTP_TIMEOUT_MILLIS = 300_000
    }
}
