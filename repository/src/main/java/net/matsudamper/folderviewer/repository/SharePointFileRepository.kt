package net.matsudamper.folderviewer.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
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
import com.microsoft.graph.serviceclient.GraphServiceClient
import net.matsudamper.folderviewer.common.FileObjectId
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
                    id = FileObjectId.Item(item.id),
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

    override suspend fun getFileContent(fileId: FileObjectId.Item): InputStream = withContext(Dispatchers.IO) {
        val driveId = getDriveId()

        graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(fileId.id).content().get()
            ?: ByteArrayInputStream(ByteArray(0))
    }

    override suspend fun getFileSize(fileId: FileObjectId.Item): Long = withContext(Dispatchers.IO) {
        val item = graphApiClient.getDriveItem(fileId.id)
        item.size ?: 0L
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
    ) {
        withContext(Dispatchers.IO) {
            val driveId = getDriveId()
            val parentId = when (id) {
                is FileObjectId.Root -> return@withContext
                is FileObjectId.Item -> id.id
            }

            coroutineScope {
                val progressInputStream = ProgressInputStream(inputStream)
                val job = launch {
                    progressInputStream.onRead.collect(onRead)
                }

                uploadWithSession(driveId, parentId, fileName, progressInputStream, size)

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

            val createdFolder = graphServiceClient.drives().byDriveId(driveId)
                .items()
                .byDriveItemId(
                    when (id) {
                        is FileObjectId.Root -> return@withContext
                        is FileObjectId.Item -> id.id
                    },
                )
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
                    uploadedSize += fileToUpload.size ?: 0L
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
    ) {
        val uploadSession = graphServiceClient.drives().byDriveId(driveId)
            .items().byDriveItemId("$parentId:/$fileName:")
            .createUploadSession()
            .post(CreateUploadSessionPostRequestBody())
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
    ) {
        withContext(Dispatchers.IO) {
            val driveId = getDriveId()

            val parentId = when (id) {
                is FileObjectId.Root -> return@withContext
                is FileObjectId.Item -> id.id
            }

            createOrGetFolder(driveId, parentId, directoryName)
        }
    }

    private companion object {
        private const val UPLOAD_CHUNK_SIZE = 5 * 1024 * 1024
    }
}
