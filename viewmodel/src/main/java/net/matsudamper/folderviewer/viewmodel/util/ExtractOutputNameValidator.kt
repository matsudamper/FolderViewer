package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import java.io.IOException

internal object ExtractOutputNameValidator {
    sealed interface Result {
        data class Valid(
            val name: String,
        ) : Result

        data object Invalid : Result
    }

    fun validate(name: String): Result {
        if (name.isBlank()) return Result.Invalid
        if (name == "." || name == "..") return Result.Invalid
        if (name.startsWith("/") || name.startsWith("\\")) return Result.Invalid
        if (name.contains('/') || name.contains('\\')) return Result.Invalid
        return Result.Valid(name)
    }

    fun resolveChildFile(parentPath: String, name: String): File? {
        if (validate(name) !is Result.Valid) return null
        val parentDir = File(parentPath)
        val child = File(parentDir, name)
        return try {
            val parentCanonical = parentDir.canonicalPath
            val childCanonical = child.canonicalPath
            if (child.parentFile?.canonicalPath != parentCanonical) return null
            if (!childCanonical.startsWith(parentCanonical + File.separator)) return null
            child
        } catch (_: IOException) {
            null
        }
    }
}
