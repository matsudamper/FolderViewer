package net.matsudamper.folderviewer.viewmodel.util

import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiState

@Suppress("FunctionName")
fun <T> FileSortComparator(
    config: FolderBrowserUiState.FileSortConfig,
    sizeProvider: (T) -> Comparable<*>,
    lastModifiedProvider: (T) -> Comparable<*>,
    nameProvider: (T) -> String,
): Comparator<T> {
    val comparator: Comparator<T> = when (config.key) {
        FolderBrowserUiState.FileSortKey.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { nameProvider(it) }
        FolderBrowserUiState.FileSortKey.Date -> compareBy { lastModifiedProvider(it) }
        FolderBrowserUiState.FileSortKey.Size -> compareBy { sizeProvider(it) }
    }

    return if (config.isAscending) comparator else comparator.reversed()
}
