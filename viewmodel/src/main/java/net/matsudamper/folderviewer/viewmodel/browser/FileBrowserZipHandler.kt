package net.matsudamper.folderviewer.viewmodel.browser

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.viewmodel.util.ZipFileUtil

internal class FileBrowserZipHandler(
    private val sendSnackbar: suspend (String) -> Unit,
    private val trySendSnackbar: (String) -> Unit,
) {
    suspend fun compress(
        items: List<FileItem>,
        fileName: String,
        repository: FileRepository,
        localFolderPath: String?,
        onCompleted: suspend () -> Unit,
    ) {
        runCatching {
            val sourceFiles = items.map { item ->
                when (val uri = repository.getViewSourceUri(item.id)) {
                    is ViewSourceUri.LocalFile -> File(uri.path)

                    is ViewSourceUri.RemoteUrl,
                    is ViewSourceUri.StreamProvider,
                    -> {
                        sendSnackbar("圧縮はローカルストレージのみ対応しています")
                        return
                    }
                }
            }
            val parentPath = localFolderPath ?: run {
                sendSnackbar("圧縮はローカルストレージのみ対応しています")
                return
            }
            val zipFile = File(parentPath, "$fileName.zip")
            if (zipFile.exists()) {
                sendSnackbar("同じ名前のファイルが既に存在します: ${zipFile.name}")
                return
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    ZipFileUtil.compressFiles(sourceFiles, zipFile)
                }.onFailure { e ->
                    zipFile.delete()
                    throw e
                }
            }
            onCompleted()
            sendSnackbar("${zipFile.name}を作成しました")
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    e.printStackTrace()
                    trySendSnackbar("圧縮に失敗しました: ${e.message}")
                }
            }
        }
    }

    suspend fun extract(
        zipFileItem: FileItem,
        folderName: String,
        repository: FileRepository,
        localFolderPath: String?,
        onCompleted: suspend () -> Unit,
    ) {
        runCatching {
            val zipFile = when (val uri = repository.getViewSourceUri(zipFileItem.id)) {
                is ViewSourceUri.LocalFile -> File(uri.path)

                is ViewSourceUri.RemoteUrl,
                is ViewSourceUri.StreamProvider,
                -> {
                    sendSnackbar("解凍はローカルストレージのみ対応しています")
                    return
                }
            }
            val parentPath = localFolderPath ?: run {
                sendSnackbar("解凍はローカルストレージのみ対応しています")
                return
            }
            val extractDir = File(parentPath, folderName)
            if (extractDir.exists()) {
                sendSnackbar("同じ名前のフォルダが既に存在します: ${extractDir.name}")
                return
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    ZipFileUtil.extractZip(zipFile, extractDir)
                }.onFailure { e ->
                    extractDir.deleteRecursively()
                    throw e
                }
            }
            onCompleted()
            sendSnackbar("${extractDir.name}に展開しました")
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    e.printStackTrace()
                    trySendSnackbar("解凍に失敗しました: ${e.message}")
                }
            }
        }
    }

    fun isSingleZipFileSelected(
        selectedItems: Set<FileObjectId.Item>,
        rawFiles: List<FileItem>,
    ): Boolean {
        if (selectedItems.size != 1) return false
        val fileItem = rawFiles.find { it.id == selectedItems.first() } ?: return false
        return !fileItem.isDirectory && fileItem.displayPath.endsWith(".zip", ignoreCase = true)
    }
}
