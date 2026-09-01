package net.matsudamper.folderviewer.viewmodel.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object ZipFileUtil {
    fun addZipEntry(zipOut: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            zipOut.putNextEntry(ZipEntry("$entryName/"))
            zipOut.closeEntry()
            file.listFiles()?.forEach { child ->
                addZipEntry(zipOut, child, "$entryName/${child.name}")
            }
        } else {
            zipOut.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { input -> input.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    fun compressFiles(sourceFiles: List<File>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
            sourceFiles.forEach { file -> addZipEntry(zipOut, file, file.name) }
        }
    }

    fun extractZip(zipFile: File, destDir: File) {
        destDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipIn ->
            generateSequence { zipIn.nextEntry }.forEach { entry ->
                extractZipEntry(zipIn, entry, destDir)
                zipIn.closeEntry()
            }
        }
    }

    fun zipFileDefaultFolderName(fileName: String): String {
        return if (fileName.endsWith(".zip", ignoreCase = true)) {
            fileName.dropLast(4)
        } else {
            fileName
        }
    }

    private fun extractZipEntry(zipIn: ZipInputStream, entry: ZipEntry, destDir: File) {
        val entryFile = File(destDir, entry.name)
        validateZipEntryPath(destDir, entryFile)
        if (entry.isDirectory) {
            entryFile.mkdirs()
            return
        }
        entryFile.parentFile?.mkdirs()
        entryFile.outputStream().use { output ->
            zipIn.copyTo(output)
        }
    }

    private fun validateZipEntryPath(destDir: File, entryFile: File) {
        val destPath = destDir.canonicalPath
        val entryPath = entryFile.canonicalPath
        if (!entryPath.startsWith(destPath + File.separator) && entryPath != destPath) {
            throw SecurityException("Invalid zip entry path")
        }
    }
}
