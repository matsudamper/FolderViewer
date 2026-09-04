package net.matsudamper.folderviewer.viewmodel.util

import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.viewmodel.browser.ExtractableFileType

internal object ExtractOutputConflictChecker {
    enum class OutputKind {
        Folder,
        File,
    }

    sealed class Conflict(
        val message: String,
    ) {
        class FolderExists(
            name: String,
        ) : Conflict("同じ名前のフォルダが既に存在します: $name")

        class FileExists(
            name: String,
        ) : Conflict("同じ名前のファイルが既に存在します: $name")
    }

    fun findConflict(
        parentPath: String,
        outputName: String,
        outputKind: OutputKind,
    ): Conflict? {
        val outputFile = ExtractOutputNameValidator.resolveChildFile(parentPath, outputName) ?: return null
        if (!outputFile.exists()) {
            return null
        }
        return when (outputKind) {
            OutputKind.Folder -> Conflict.FolderExists(outputFile.name)
            OutputKind.File -> Conflict.FileExists(outputFile.name)
        }
    }

    fun findAnyConflict(
        parentPath: String,
        outputName: String,
    ): Conflict? {
        val outputFile = ExtractOutputNameValidator.resolveChildFile(parentPath, outputName) ?: return null
        if (!outputFile.exists()) {
            return null
        }
        return if (outputFile.isDirectory) {
            Conflict.FolderExists(outputFile.name)
        } else {
            Conflict.FileExists(outputFile.name)
        }
    }

    fun requireNoConflict(
        parentPath: String,
        outputName: String,
        outputKind: OutputKind,
    ) {
        findConflict(parentPath, outputName, outputKind)?.let { conflict ->
            error(conflict.message)
        }
    }

    fun requireNoAnyConflict(
        parentPath: String,
        outputName: String,
    ) {
        findAnyConflict(parentPath, outputName)?.let { conflict ->
            error(conflict.message)
        }
    }

    fun conflictMessage(
        parentPath: String,
        outputName: String,
        extractType: ExtractableFileType,
    ): String? {
        return conflictForExtractableType(parentPath, outputName, extractType)?.message
    }

    fun conflictMessage(
        parentPath: String,
        outputName: String,
        extractType: ExtractJobRepository.ExtractType,
    ): String? {
        return conflictForJobType(parentPath, outputName, extractType)?.message
    }

    fun requireNoConflict(
        meta: ExtractJobRepository.ExtractJobMeta,
    ) {
        conflictForJobType(meta.localFolderPath, meta.outputName, meta.extractType)?.let { conflict ->
            error(conflict.message)
        }
    }

    private fun conflictForExtractableType(
        parentPath: String,
        outputName: String,
        extractType: ExtractableFileType,
    ): Conflict? {
        return when (extractType) {
            ExtractableFileType.Zip -> findConflict(parentPath, outputName, OutputKind.Folder)

            ExtractableFileType.TarGz,
            ExtractableFileType.TarXz,
            ExtractableFileType.TarZst,
            -> findAnyConflict(parentPath, outputName)

            is ExtractableFileType.Compressed -> findConflict(parentPath, outputName, OutputKind.File)
        }
    }

    private fun conflictForJobType(
        parentPath: String,
        outputName: String,
        extractType: ExtractJobRepository.ExtractType,
    ): Conflict? {
        return when (extractType) {
            ExtractJobRepository.ExtractType.Zip -> findConflict(parentPath, outputName, OutputKind.Folder)

            ExtractJobRepository.ExtractType.TarGz,
            ExtractJobRepository.ExtractType.TarXz,
            ExtractJobRepository.ExtractType.TarZst,
            -> findAnyConflict(parentPath, outputName)

            ExtractJobRepository.ExtractType.Zst,
            ExtractJobRepository.ExtractType.Xz,
            -> findConflict(parentPath, outputName, OutputKind.File)
        }
    }
}
