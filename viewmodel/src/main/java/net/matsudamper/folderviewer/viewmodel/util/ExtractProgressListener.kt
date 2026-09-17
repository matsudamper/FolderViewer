package net.matsudamper.folderviewer.viewmodel.util

internal class ExtractProgressListener(
    private val fileStartedHandler: ((String) -> Unit)? = null,
    private val fileCompletedHandler: (() -> Unit)? = null,
    private val bytesTransferredHandler: ((Long) -> Unit)? = null,
    private val cancellationCheckHandler: (() -> Unit)? = null,
) {
    fun onFileStarted(name: String) {
        fileStartedHandler?.invoke(name)
    }

    fun onFileCompleted() {
        fileCompletedHandler?.invoke()
    }

    fun onBytesTransferred(bytes: Long) {
        bytesTransferredHandler?.invoke(bytes)
    }

    fun checkCancellation() {
        cancellationCheckHandler?.invoke()
    }
}
