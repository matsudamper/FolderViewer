package net.matsudamper.folderviewer.coil

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.Options
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.FileRepositoryResult
import okio.buffer
import okio.source

public object CoilImageLoaderFactory {
    public fun create(context: Context, fileRepository: FileRepository?): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                if (fileRepository != null) {
                    add(FileRepositoryImageFetcherFactory(fileRepository))
                }
                add(MaxSizeInterceptor(4096))
            }
            .build()
    }
}

private class FileRepositoryImageFetcherFactory(
    private val fileRepository: FileRepository,
) : Fetcher.Factory<Any> {
    override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
        return if (data is FileImageSource) {
            FileRepositoryImageFetcher(data, options, fileRepository)
        } else {
            null
        }
    }
}

private class FileRepositoryImageFetcher(
    private val fileImageSource: FileImageSource,
    private val options: Options,
    private val fileRepository: FileRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val path = when (fileImageSource) {
            is FileImageSource.Thumbnail -> fileImageSource.path
            is FileImageSource.Original -> fileImageSource.path
        }

        val result = fileRepository.getFileContent(path)
        val inputStream = when (result) {
            is FileRepositoryResult.Success -> result.value
            is FileRepositoryResult.Error -> throw result.throwable
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

        val key = when (val data = request.data) {
            is FileImageSource.Thumbnail -> "thumbnail:${data.path}"
            is FileImageSource.Original -> null
            else -> null
        }

        return if (isWidthTooLarge || isHeightTooLarge || key != null) {
            val newSize = if (isWidthTooLarge || isHeightTooLarge) Size(maxSize, maxSize) else size
            val newRequest = request.newBuilder().also {
                if (isWidthTooLarge || isHeightTooLarge) {
                    it.precision(Precision.INEXACT)
                }
                if (key != null) {
                    it.diskCacheKey(key)
                    it.memoryCacheKey(key)
                }
            }.build()
            chain.withSize(newSize).proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}
