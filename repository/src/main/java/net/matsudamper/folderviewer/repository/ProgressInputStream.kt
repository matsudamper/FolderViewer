package net.matsudamper.folderviewer.repository

import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ProgressInputStream(
    private val inputStream: InputStream,
) : InputStream() {
    private val _onRead = MutableStateFlow(0L)
    val onRead: Flow<Long> = _onRead.asStateFlow()

    private var markValue: Long = 0

    override fun read(): Int {
        val b = inputStream.read()
        if (b != -1) {
            _onRead.update { it + 1 }
        }
        return b
    }

    override fun read(b: ByteArray): Int {
        val count = inputStream.read(b)
        if (count != -1) {
            _onRead.update { it + count }
        }
        return count
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = inputStream.read(b, off, len)
        if (count != -1) {
            _onRead.update { it + count }
        }
        return count
    }

    override fun skip(n: Long): Long {
        val count = inputStream.skip(n)
        _onRead.update { it + count }
        return count
    }

    override fun available(): Int = inputStream.available()

    override fun close() {
        inputStream.close()
    }

    override fun mark(readlimit: Int) {
        inputStream.mark(readlimit)
        markValue = _onRead.value
    }

    override fun reset() {
        inputStream.reset()
        _onRead.value = markValue
    }

    override fun markSupported(): Boolean = inputStream.markSupported()
}
