package net.matsudamper.folderviewer.viewmodel.browser

import android.content.Context
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.browser.ExtractDialogMode
import net.matsudamper.folderviewer.viewmodel.util.CompressedFileUtil
import net.matsudamper.folderviewer.viewmodel.util.ExtractableFileNameUtil
import net.matsudamper.folderviewer.viewmodel.util.ExtractMediaScanner
import net.matsudamper.folderviewer.viewmodel.util.ExtractOutputNameValidator
import net.matsudamper.folderviewer.viewmodel.util.ZipFileUtil

internal sealed interface ExtractableFileType {
    data object Zip : ExtractableFileType

    data class Compressed(
        val format: CompressedFileUtil.Format,
    ) : ExtractableFileType
}

internal data class ExtractContext(
    val repository: FileRepository,
    val localFolderPath: String?,
    val onCompleted: suspend () -> Unit,
)

internal data class ExtractDialogInput(
    val fileItem: FileItem,
    val outputName: String,
    val extractType: ExtractableFileType,
    val mode: ExtractDialogMode,
)

internal class FileBrowserZipHandler(
    private val appContext: Context,
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

    suspend fun extractZip(
        zipFileItem: FileItem,
        folderName: String,
        context: ExtractContext,
    ) {
        runCatching {
            val zipFile = resolveLocalFile(zipFileItem, context.repository) ?: return@runCatching
            val parentPath = context.localFolderPath ?: run {
                sendSnackbar("解凍はローカルストレージのみ対応しています")
                return
            }
            val extractDir = ExtractOutputNameValidator.resolveChildFile(parentPath, folderName)
                ?: run {
                    sendSnackbar("無効なフォルダ名です")
                    return
                }
            withContext(Dispatchers.IO) {
                val extractedFiles = ZipFileUtil.extractZip(zipFile, extractDir)
                ExtractMediaScanner.scanExtractedMediaFiles(appContext, extractedFiles)
            }
            context.onCompleted()
            sendSnackbar("${extractDir.name}に展開しました")
        }.onFailure { e ->
            handleExtractFailure(e)
        }
    }

    fun createExtractDialogInput(fileItem: FileItem): ExtractDialogInput? {
        val extractType = getExtractableFileType(fileItem) ?: return null
        return ExtractDialogInput(
            fileItem = fileItem,
            outputName = defaultExtractName(fileItem, extractType),
            extractType = extractType,
            mode = extractType.toExtractDialogMode(),
        )
    }

    fun isSingleExtractableFileSelected(
        selectedItems: Set<FileObjectId.Item>,
        rawFiles: List<FileItem>,
    ): Boolean {
        if (selectedItems.size != 1) return false
        val fileItem = rawFiles.find { it.id == selectedItems.first() } ?: return false
        return getExtractableFileType(fileItem) != null
    }

    fun getExtractableFileType(fileItem: FileItem): ExtractableFileType? {
        if (fileItem.isDirectory) return null
        return ExtractableFileNameUtil.detect(fileItem.displayPath)
    }

    fun defaultExtractName(fileItem: FileItem, type: ExtractableFileType): String {
        return ExtractableFileNameUtil.defaultOutputName(fileItem.displayPath, type)
    }

    private suspend fun resolveLocalFile(
        fileItem: FileItem,
        repository: FileRepository,
    ): File? {
        return when (val uri = repository.getViewSourceUri(fileItem.id)) {
            is ViewSourceUri.LocalFile -> File(uri.path)

            is ViewSourceUri.RemoteUrl,
            is ViewSourceUri.StreamProvider,
            -> {
                sendSnackbar("解凍はローカルストレージのみ対応しています")
                null
            }
        }
    }

    private fun handleExtractFailure(e: Throwable) {
        when (e) {
            is CancellationException -> throw e
            is ZipFileUtil.ExtractException -> trySendSnackbar(e.message ?: "解凍に失敗しました")
            else -> {
                e.printStackTrace()
                trySendSnackbar("解凍に失敗しました: ${e.message}")
            }
        }
    }
}

private fun ExtractableFileType.toExtractDialogMode(): ExtractDialogMode {
    return when (this) {
        ExtractableFileType.Zip -> ExtractDialogMode.ZipFolder
        is ExtractableFileType.Compressed -> when (format) {
            CompressedFileUtil.Format.Zst -> ExtractDialogMode.ZstFile
            CompressedFileUtil.Format.Xz -> ExtractDialogMode.XzFile
        }
    }
}
