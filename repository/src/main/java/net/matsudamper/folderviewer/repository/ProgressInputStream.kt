package net.matsudamper.folderviewer.repository

import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProgressInputStream(
    private val inputStream: InputStream,
) : InputStream() {
    private val _onRead = MutableStateFlow<Long>(0)
    val onRead: Flow<Long> = _onRead.asStateFlow()

    private var totalBytesRead: Long = 0
    private var markTotalBytesRead: Long = 0

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
        markTotalBytesRead = totalBytesRead
    }

    override fun reset() {
        inputStream.reset()
        totalBytesRead = markTotalBytesRead
        _onRead.value = totalBytesRead
    }

    private fun updateProgress(bytes: Long) {
        totalBytesRead += bytes
        _onRead.value = totalBytesRead
    }

    override fun markSupported(): Boolean = inputStream.markSupported()
}
