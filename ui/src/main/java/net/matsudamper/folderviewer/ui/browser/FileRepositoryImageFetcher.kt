package net.matsudamper.folderviewer.ui.browser

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import net.matsudamper.folderviewer.repository.FileRepository
import okio.buffer
import okio.source

class FileRepositoryImageFetcher(
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

    class Factory(private val fileRepository: FileRepository) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data is FileImageSource) {
                FileRepositoryImageFetcher(data, options, fileRepository)
            } else {
                null
            }
        }
    }
}
