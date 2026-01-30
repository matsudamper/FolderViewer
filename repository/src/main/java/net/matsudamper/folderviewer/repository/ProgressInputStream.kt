package net.matsudamper.folderviewer.repository

import java.io.InputStream
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ProgressInputStream(
    private val inputStream: InputStream,
) : InputStream() {
    private val _onRead = MutableSharedFlow<Long>(
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val onRead: Flow<Long> = _onRead.asSharedFlow()

    override fun read(): Int {
        val b = inputStream.read()
        if (b != -1) {
            _onRead.tryEmit(1)
        }
        return b
    }

    override fun read(b: ByteArray): Int {
        val count = inputStream.read(b)
        if (count != -1) {
            _onRead.tryEmit(count.toLong())
        }
        return count
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = inputStream.read(b, off, len)
        if (count != -1) {
            _onRead.tryEmit(count.toLong())
        }
        return count
    }

    override fun skip(n: Long): Long {
        val count = inputStream.skip(n)
        _onRead.tryEmit(count)
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
}
