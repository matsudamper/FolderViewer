package net.matsudamper.folderviewer.viewmodel.util

import net.matsudamper.folderviewer.repository.ExtractJobRepository

internal object ExtractOutputConflictChecker {
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

    fun findConflictForCompressedOutput(
        parentPath: String,
        outputName: String,
    ): Conflict? {
        findConflict(parentPath, outputName)?.let { return it }
        val apkCandidate = DecompressedOutputNameResolver.apkCandidateName(outputName) ?: return null
        return findConflict(parentPath, apkCandidate)
    }

    fun requireNoConflict(
        parentPath: String,
        outputName: String,
    ) {
        findConflict(parentPath, outputName)?.let { conflict ->
            error(conflict.message)
        }
    }

    fun conflictMessage(
        parentPath: String,
        outputName: String,
    ): String? {
        return findConflict(parentPath, outputName)?.message
    }

    fun conflictMessageForCompressedOutput(
        parentPath: String,
        outputName: String,
    ): String? {
        return findConflictForCompressedOutput(parentPath, outputName)?.message
    }

    fun requireNoConflict(
        meta: ExtractJobRepository.ExtractJobMeta,
    ) {
        val conflict = when (meta.extractType) {
            ExtractJobRepository.ExtractType.Zst,
            ExtractJobRepository.ExtractType.Xz,
            -> findConflictForCompressedOutput(meta.localFolderPath, meta.outputName)

            else -> findConflict(meta.localFolderPath, meta.outputName)
        }
        conflict?.let { error(it.message) }
    }
}
