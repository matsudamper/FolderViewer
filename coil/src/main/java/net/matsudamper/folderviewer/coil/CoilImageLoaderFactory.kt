package net.matsudamper.folderviewer.coil

import android.content.Context
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.intercept.Interceptor
import coil.key.Keyer
import coil.request.ImageResult
import coil.request.Options
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import coil.util.DebugLogger
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import okio.buffer
import okio.source

object CoilImageLoaderFactory {
    fun create(context: Context, storageRepository: StorageRepository): ImageLoader {
        return ImageLoader.Builder(context)
            .logger(DebugLogger())
            .components {
                add(FileImageSourceKeyer())
                add(FileRepositoryImageFetcherFactory(storageRepository))
                add(MaxSizeInterceptor(4096))
            }
            .build()
    }

    internal const val DEFAULT_THUMBNAIL_SIZE = 256
}

private class FileImageSourceKeyer : Keyer<FileImageSource> {
    override fun key(data: FileImageSource, options: Options): String? {
        return when (data) {
            is FileImageSource.Thumbnail -> "thumbnail:${data.fileId.storageId.id}:${data.fileId.id}"
            is FileImageSource.Original -> null
        }
    }
}

private class FileRepositoryImageFetcherFactory(
    private val storageRepository: StorageRepository,
) : Fetcher.Factory<Any> {
    override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
        return if (data is FileImageSource) {
            FileRepositoryImageFetcher(data, options, imageLoader.diskCache, storageRepository)
        } else {
            null
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
private class FileRepositoryImageFetcher(
    private val fileImageSource: FileImageSource,
    private val options: Options,
    private val diskCache: DiskCache?,
    private val storageRepository: StorageRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val fileRepository = storageRepository.getFileRepository(fileImageSource.fileId.storageId)
            ?: throw IllegalStateException("Storage not found: ${fileImageSource.fileId.storageId}")

        val thumbnailSize = when (val width = options.size.width) {
            is Dimension.Pixels -> width.px
            else -> CoilImageLoaderFactory.DEFAULT_THUMBNAIL_SIZE
        }

        if (diskCache == null) {
            return createStreamResult(openInputStream(fileRepository, thumbnailSize))
        }

        val diskCacheKey = createDiskCacheKey(fileRepository.getFileInfo(fileImageSource.fileId), thumbnailSize)

        if (options.diskCachePolicy.readEnabled) {
            val snapshot = diskCache.openSnapshot(diskCacheKey)
            if (snapshot != null) {
                return createSnapshotResult(diskCache, diskCacheKey, snapshot, DataSource.DISK)
            }
        }

        val inputStream = openInputStream(fileRepository, thumbnailSize)
        if (!options.diskCachePolicy.writeEnabled) {
            return createStreamResult(inputStream)
        }

        val editor = diskCache.openEditor(diskCacheKey)
            ?: return createStreamResult(inputStream)

        val snapshot = try {
            withContext(Dispatchers.IO) {
                inputStream.source().use { source ->
                    diskCache.fileSystem.write(editor.data) { writeAll(source) }
                }
            }
            editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            editor.abort()
            throw e
        } ?: throw IllegalStateException("Failed to write disk cache: ${fileImageSource.fileId}")

        return createSnapshotResult(diskCache, diskCacheKey, snapshot, DataSource.NETWORK)
    }

    private suspend fun openInputStream(fileRepository: FileRepository, thumbnailSize: Int): InputStream {
        return when (fileImageSource) {
            is FileImageSource.Thumbnail -> fileRepository.getThumbnail(fileImageSource.fileId, thumbnailSize)
            is FileImageSource.Original -> fileRepository.getFileContent(fileImageSource.fileId)
        } ?: throw IllegalStateException("Failed to image load: ${fileImageSource.fileId}")
    }

    private fun createDiskCacheKey(fileItem: FileItem, thumbnailSize: Int): String {
        val fileKey = "${fileItem.id.storageId.id}:${fileItem.id.id}:${fileItem.lastModified}:${fileItem.size}"
        return when (fileImageSource) {
            is FileImageSource.Thumbnail -> "thumbnail:$thumbnailSize:$fileKey"
            is FileImageSource.Original -> "original:$fileKey"
        }
    }

    private fun createStreamResult(inputStream: InputStream): SourceResult {
        return SourceResult(
            source = ImageSource(inputStream.source().buffer(), options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    private fun createSnapshotResult(
        diskCache: DiskCache,
        diskCacheKey: String,
        snapshot: DiskCache.Snapshot,
        dataSource: DataSource,
    ): SourceResult {
        return SourceResult(
            source = ImageSource(
                file = snapshot.data,
                fileSystem = diskCache.fileSystem,
                diskCacheKey = diskCacheKey,
                closeable = snapshot,
            ),
            mimeType = null,
            dataSource = dataSource,
        )
    }
}

private class MaxSizeInterceptor(private val maxSize: Int) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val size = chain.size

        val isWidthTooLarge = when (val width = size.width) {
            is Dimension.Pixels -> width.px > maxSize
            else -> false
        }
        val isHeightTooLarge = when (val height = size.height) {
            is Dimension.Pixels -> height.px > maxSize
            else -> false
        }

        return if (isWidthTooLarge || isHeightTooLarge) {
            val newSize = Size(maxSize, maxSize)
            val newRequest = request.newBuilder()
                .precision(Precision.INEXACT)
                .build()
            chain.withSize(newSize).proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}
