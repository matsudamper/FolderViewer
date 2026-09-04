package net.matsudamper.folderviewer.viewmodel.browser

internal sealed interface EnqueueExtractResult {
    data class Success(val jobId: Long) : EnqueueExtractResult

    data class Failure(val message: String) : EnqueueExtractResult
}
