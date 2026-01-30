package net.matsudamper.folderviewer.repository

import java.io.InputStream
import java.io.OutputStream

fun InputStream.copyToWithProgress(
    out: OutputStream,
    totalSize: Long,
    onProgress: (Float) -> Unit,
    bufferSize: Int = 8 * 1024,
): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        if (totalSize > 0) {
            onProgress(bytesCopied.toFloat() / totalSize)
        }
        bytes = read(buffer)
    }
    return bytesCopied
}

class ProgressInputStream(
    private val inputStream: InputStream,
    private val totalSize: Long,
    private val onProgress: (Float) -> Unit,
) : InputStream() {
    private var bytesRead: Long = 0

    override fun read(): Int {
        val b = inputStream.read()
        if (b != -1) {
            updateProgress(1)
        }
        return b
    }

    override fun read(b: ByteArray): Int {
        val count = inputStream.read(b)
        if (count != -1) {
            updateProgress(count.toLong())
        }
        return count
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = inputStream.read(b, off, len)
        if (count != -1) {
            updateProgress(count.toLong())
        }
        return count
    }

    override fun skip(n: Long): Long {
        val count = inputStream.skip(n)
        updateProgress(count)
        return count
    }

    override fun available(): Int = inputStream.available()

    override fun close() {
        inputStream.close()
    }

    override fun mark(readlimit: Int) {
        inputStream.mark(readlimit)
    }

    override fun reset() {
        inputStream.reset()
    }

    override fun markSupported(): Boolean = inputStream.markSupported()

    private fun updateProgress(bytes: Long) {
        bytesRead += bytes
        if (totalSize > 0) {
            onProgress(bytesRead.toFloat() / totalSize)
        }
    }
}
