package net.matsudamper.folderviewer.ui.browser

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
import net.matsudamper.folderviewer.repository.FileRepository
import okio.buffer
import okio.source

class FileRepositoryImageFetcher private constructor(
    private val fileImageSource: FileImageSource,
    private val options: Options,
    private val fileRepository: FileRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val inputStream = when (fileImageSource) {
            is FileImageSource.Thumbnail -> {
                fileRepository.getThumbnailContent(fileImageSource.fileItem.path)
                    ?: fileRepository.getFileContent(fileImageSource.fileItem.path)
            }

            is FileImageSource.Original -> {
                fileRepository.getFileContent(fileImageSource.fileItem.path)
            }
        }
        val bufferedSource = inputStream.source().buffer()

        return SourceResult(
            source = ImageSource(bufferedSource, options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    private class Factory(private val fileRepository: FileRepository) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data is FileImageSource) {
                FileRepositoryImageFetcher(data, options, fileRepository)
            } else {
                null
            }
        }
    }

    /**
     * 指定されたサイズ（maxSize）を超えるリクエストを制限するインターセプター。
     * Size.ORIGINAL が指定された場合も、この上限に制限します。
     */
    private class MaxSizeInterceptor(private val maxSize: Int) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val size = chain.size

            val isWidthTooLarge = when (val width = size.width) {
                is Dimension.Pixels -> width.px > maxSize
                else -> true
            }
            val isHeightTooLarge = when (val height = size.height) {
                is Dimension.Pixels -> height.px > maxSize
                else -> true
            }

            return if (isWidthTooLarge || isHeightTooLarge) {
                val newRequest = request.newBuilder()
                    .size(maxSize)
                    .precision(Precision.INEXACT)
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
    }

    companion object {
        fun createLoader(context: Context, fileRepository: FileRepository?): ImageLoader {
            return ImageLoader.Builder(context)
                .components {
                    if (fileRepository != null) {
                        add(Factory(fileRepository))
                    }
                    // グローバルにサイズ制限をかけるインターセプターを追加
                    add(MaxSizeInterceptor(4096))
                }
                .build()
        }
    }
}
