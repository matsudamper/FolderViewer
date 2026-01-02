package net.matsudamper.folderviewer.coil

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageResult
import coil.request.Options
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import net.matsudamper.folderviewer.repository.FileRepository
import okio.buffer
import okio.source

public object CoilImageLoaderFactory {
    // メモリキャッシュサイズ: アプリメモリの25%を使用
    // 画像ビューアアプリとして、画像表示のパフォーマンスを優先
    private const val MEMORY_CACHE_SIZE_PERCENT = 0.25

    // ディスクキャッシュサイズ: キャッシュディレクトリの2%を使用
    // ネットワークストレージからの読み込みを減らすために有効化
    // サムネイルのみキャッシュし、オリジナル画像はキャッシュしない
    private const val DISK_CACHE_SIZE_PERCENT = 0.02

    public fun create(context: Context, fileRepository: FileRepository?): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_SIZE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(DISK_CACHE_SIZE_PERCENT)
                    .build()
            }
            .components {
                if (fileRepository != null) {
                    add(FileRepositoryImageFetcherFactory(fileRepository))
                }
                add(ThumbnailOnlyCacheInterceptor())
                add(MaxSizeInterceptor(4096))
            }
            .build()
    }

    /**
     * ディスクキャッシュをクリアする
     */
    public fun clearDiskCache(context: Context) {
        val cacheDir = context.cacheDir.resolve("image_cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }
}

private class ThumbnailOnlyCacheInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        
        // FileImageSource.Originalの場合はキャッシュを無効化
        val newRequest = if (request.data is FileImageSource.Original) {
            request.newBuilder()
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        } else {
            request
        }
        
        return chain.proceed(newRequest)
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

        val inputStream = when (fileImageSource) {
            is FileImageSource.Thumbnail -> {
                fileRepository.getThumbnailContent(path)
                    ?: fileRepository.getFileContent(path)
            }

            is FileImageSource.Original -> {
                fileRepository.getFileContent(path)
            }
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
