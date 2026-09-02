package net.matsudamper.folderviewer.viewmodel.worker

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object ExtractTempFileSupport {
    fun recoverOutputIfAlreadyPublished(
        appContext: Context,
        jobId: Long,
        outputFile: File,
    ): File? {
        val marker = completionMarker(appContext, jobId)
        if (!marker.exists()) {
            return null
        }
        val publishedPath = marker.readText()
        if (publishedPath != outputFile.absolutePath) {
            return null
        }
        if (!outputFile.isFile) {
            return null
        }
        return outputFile
    }

    fun createTempFile(outputDirectoryPath: String): File {
        val directory = resolveOutputDirectory(outputDirectoryPath)
        return File.createTempFile("extract-", ".tmp", directory)
    }

    fun markPublished(appContext: Context, jobId: Long, outputFile: File) {
        completionMarker(appContext, jobId).writeText(outputFile.absolutePath)
    }

    fun clearMarker(appContext: Context, jobId: Long) {
        completionMarker(appContext, jobId).delete()
    }

    fun publishTempFile(tempFile: File, outputFile: File) {
        if (outputFile.exists()) {
            error("同じ名前のファイルが既に存在します: ${outputFile.name}")
        }
        outputFile.parentFile?.mkdirs()
        if (moveTempFile(tempFile, outputFile)) {
            return
        }
        error("出力ファイルの作成に失敗しました")
    }

    fun cleanupTempFile(tempFile: File) {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    private fun resolveOutputDirectory(outputDirectoryPath: String): File {
        val outputDirectory = File(outputDirectoryPath)
        if (!outputDirectory.isDirectory && !outputDirectory.mkdirs()) {
            error("展開先ディレクトリを作成できません")
        }
        return outputDirectory
    }

    private fun moveTempFile(tempFile: File, outputFile: File): Boolean {
        val atomicMoved = runCatching {
            Files.move(
                tempFile.toPath(),
                outputFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.isSuccess
        if (atomicMoved) {
            return true
        }
        return tempFile.renameTo(outputFile)
    }

    private fun tempDirectory(appContext: Context): File {
        return File(appContext.cacheDir, "extract-temp").apply { mkdirs() }
    }

    private fun completionMarker(appContext: Context, jobId: Long): File {
        return File(tempDirectory(appContext), "extract-complete-$jobId.marker")
    }
}
