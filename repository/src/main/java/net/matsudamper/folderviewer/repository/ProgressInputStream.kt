package net.matsudamper.folderviewer.repository

import java.io.InputStream

class ProgressInputStream(
    private val inputStream: InputStream,
    private val onRead: (Long) -> Unit,
) : InputStream() {
    override fun read(): Int {
        val b = inputStream.read()
        if (b != -1) {
            onRead(1)
        }
        return b
    }

    override fun read(b: ByteArray): Int {
        val count = inputStream.read(b)
        if (count != -1) {
            onRead(count.toLong())
        }
        return count
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = inputStream.read(b, off, len)
        if (count != -1) {
            onRead(count.toLong())
        }
        return count
    }

    override fun skip(n: Long): Long {
        val count = inputStream.skip(n)
        onRead(count)
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
