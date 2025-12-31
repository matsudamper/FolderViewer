package net.matsudamper.folderviewer.ui.browser

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import okio.buffer
import okio.source

class FileRepositoryImageFetcher(
    private val fileItem: FileItem,
    private val options: Options,
    private val fileRepository: FileRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val inputStream = fileRepository.getFileContent(fileItem.path)
        val source = inputStream.source().buffer()

        return SourceResult(
            source = ImageSource(source, options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val fileRepository: FileRepository) : Fetcher.Factory<FileItem> {
        override fun create(data: FileItem, options: Options, imageLoader: ImageLoader): Fetcher {
            return FileRepositoryImageFetcher(data, options, fileRepository)
        }
    }
}
