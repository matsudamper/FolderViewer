package net.matsudamper.folderviewer.coil

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
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
            is FileImageSource.Thumbnail -> "thumbnail:${data.storageId}:${data.path}"
            is FileImageSource.Original -> null
        }
    }
}

private class FileRepositoryImageFetcherFactory(
    private val storageRepository: StorageRepository,
) : Fetcher.Factory<Any> {
    override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
        return if (data is FileImageSource) {
            FileRepositoryImageFetcher(data, options, storageRepository)
        } else {
            null
        }
    }
}

private class FileRepositoryImageFetcher(
    private val fileImageSource: FileImageSource,
    private val options: Options,
    private val storageRepository: StorageRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val path = fileImageSource.path
        val storageId = fileImageSource.storageId

        val fileRepository = storageRepository.getFileRepository(storageId)
            ?: throw IllegalStateException("Storage not found: $storageId")

        val inputStream = when (fileImageSource) {
            is FileImageSource.Thumbnail -> {
                val thumbnailSize = when (val width = options.size.width) {
                    is Dimension.Pixels -> width.px
                    else -> CoilImageLoaderFactory.DEFAULT_THUMBNAIL_SIZE
                }
                fileRepository.getThumbnail(path, thumbnailSize)
            }

            is FileImageSource.Original -> fileRepository.getFileContent(path)
        }

        val bufferedSource = inputStream.source().buffer()

        return SourceResult(
            source = ImageSource(bufferedSource, options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
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
