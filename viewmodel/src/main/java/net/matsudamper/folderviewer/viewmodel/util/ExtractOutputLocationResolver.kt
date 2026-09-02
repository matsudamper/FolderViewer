package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import kotlinx.coroutines.flow.first
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.StorageConfiguration
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri

internal object ExtractOutputLocationResolver {
    data class NavigateTarget(
        val fileId: FileObjectId,
        val displayPath: String?,
    )

    data class OpenFileTarget(
        val viewSourceUri: ViewSourceUri,
        val fileName: String,
        val mimeType: String?,
    )

    data class OpenFolderTarget(
        val absolutePath: String,
    )

    suspend fun resolveNavigateToOutput(
        meta: ExtractJobRepository.ExtractJobMeta,
        storageRepository: StorageRepository,
    ): NavigateTarget? {
        resolveInternalNavigation(meta, storageRepository)?.let { return it }
        val outputPath = outputAbsolutePath(meta) ?: return null
        val outputFile = File(outputPath)
        if (!outputFile.exists()) {
            return null
        }
        val folderPath = if (outputFile.isDirectory) {
            outputFile.absolutePath
        } else {
            outputFile.parentFile?.absolutePath ?: return null
        }
        return resolveLocalStorageNavigation(folderPath, storageRepository)
    }

    suspend fun resolveOpenOutputFile(
        meta: ExtractJobRepository.ExtractJobMeta,
        storageRepository: StorageRepository,
    ): OpenFileTarget? {
        val outputPath = outputAbsolutePath(meta) ?: return null
        val outputFile = File(outputPath)
        if (!outputFile.isFile) {
            return null
        }
        resolveInternalOpenFile(meta, storageRepository)?.let { return it }
        if (!outputFile.canRead()) {
            return null
        }
        return OpenFileTarget(
            viewSourceUri = ViewSourceUri.LocalFile(outputFile.absolutePath),
            fileName = outputFile.name,
            mimeType = FileUtil.getMimeType(outputFile.name),
        )
    }

    suspend fun resolveOpenOutputFolder(
        meta: ExtractJobRepository.ExtractJobMeta,
        storageRepository: StorageRepository,
    ): OpenFolderTarget? {
        if (resolveNavigateToOutput(meta, storageRepository) != null) {
            return null
        }
        val outputPath = outputAbsolutePath(meta) ?: return null
        val outputFile = File(outputPath)
        if (!outputFile.exists()) {
            return null
        }
        val folderPath = if (outputFile.isDirectory) {
            outputFile.absolutePath
        } else {
            outputFile.parentFile?.absolutePath ?: return null
        }
        return OpenFolderTarget(absolutePath = folderPath)
    }

    private suspend fun resolveInternalNavigation(
        meta: ExtractJobRepository.ExtractJobMeta,
        storageRepository: StorageRepository,
    ): NavigateTarget? {
        if (meta.isExternalJob) {
            return null
        }
        val outputPath = meta.outputAbsolutePath ?: return null
        val outputFile = File(outputPath)
        val parentFileObjectId = meta.parentFileObjectId ?: return null
        val repository = storageRepository.getFileRepository(parentFileObjectId.storageId)
            ?: return null
        if (outputFile.isDirectory) {
            val folder = repository.getFiles(parentFileObjectId)
                .find { it.isDirectory && it.displayPath == meta.outputName }
                ?: return null
            val displayPath = if (meta.parentDisplayPath.isEmpty()) {
                folder.displayPath
            } else {
                "${meta.parentDisplayPath}/${folder.displayPath}"
            }
            return NavigateTarget(
                fileId = folder.id,
                displayPath = displayPath,
            )
        }
        val fileExists = repository.getFiles(parentFileObjectId)
            .any { !it.isDirectory && it.displayPath == meta.outputName }
        if (!fileExists) {
            return null
        }
        return NavigateTarget(
            fileId = parentFileObjectId,
            displayPath = meta.parentDisplayPath.ifEmpty { null },
        )
    }

    private suspend fun resolveInternalOpenFile(
        meta: ExtractJobRepository.ExtractJobMeta,
        storageRepository: StorageRepository,
    ): OpenFileTarget? {
        if (meta.isExternalJob) {
            return null
        }
        val parentFileObjectId = meta.parentFileObjectId ?: return null
        val repository = storageRepository.getFileRepository(parentFileObjectId.storageId)
            ?: return null
        val fileItem = repository.getFiles(parentFileObjectId)
            .find { !it.isDirectory && it.displayPath == meta.outputName }
            ?: return null
        val viewSourceUri = repository.getViewSourceUri(fileItem.id)
        return OpenFileTarget(
            viewSourceUri = viewSourceUri,
            fileName = fileItem.displayPath,
            mimeType = FileUtil.getMimeType(fileItem.displayPath),
        )
    }

    private suspend fun resolveLocalStorageNavigation(
        folderAbsolutePath: String,
        storageRepository: StorageRepository,
    ): NavigateTarget? {
        val folder = File(folderAbsolutePath)
        if (!folder.isDirectory) {
            return null
        }
        val storages = storageRepository.storageList.first()
        val storage = storages.firstOrNull { config ->
            val rootPath = when (config) {
                is StorageConfiguration.Local -> config.rootPath
                is StorageConfiguration.External -> config.rootPath
                else -> return@firstOrNull false
            }
            folder.canonicalPath.startsWith(File(rootPath).canonicalPath + File.separator) ||
                folder.canonicalPath == File(rootPath).canonicalPath
        } ?: return null
        val rootPath = when (storage) {
            is StorageConfiguration.Local -> storage.rootPath
            is StorageConfiguration.External -> storage.rootPath
            else -> return null
        }
        val relativePath = folder.canonicalPath
            .removePrefix(File(rootPath).canonicalPath)
            .trimStart(File.separatorChar)
            .replace(File.separatorChar, '/')
        val fileId = if (relativePath.isEmpty()) {
            FileObjectId.Root(storage.id)
        } else {
            FileObjectId.Item(storage.id, relativePath)
        }
        return NavigateTarget(
            fileId = fileId,
            displayPath = null,
        )
    }

    private fun outputAbsolutePath(meta: ExtractJobRepository.ExtractJobMeta): String? {
        return meta.outputAbsolutePath
            ?: File(meta.localFolderPath, meta.outputName).absolutePath.takeIf {
                File(it).exists()
            }
    }
}
