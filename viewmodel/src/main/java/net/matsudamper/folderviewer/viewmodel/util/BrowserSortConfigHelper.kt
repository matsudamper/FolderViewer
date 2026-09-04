package net.matsudamper.folderviewer.viewmodel.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.matsudamper.folderviewer.common.StorageId
import net.matsudamper.folderviewer.repository.PreferencesRepository
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.folder.FolderBrowserUiState

internal object BrowserSortConfigHelper {
    fun fileBrowserSortConfigFlow(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
    ): Flow<FileBrowserUiState.FileSortConfig> {
        return preferencesRepository.fileBrowserSortConfigForPath(
            PreferencesRepository.sortConfigPathKey(storageId, displayPath),
        ).map { it.toFileBrowserUiState() }
    }

    suspend fun saveFileBrowserSortConfig(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
        config: FileBrowserUiState.FileSortConfig,
    ) {
        preferencesRepository.saveFileBrowserSortConfigForPath(
            pathKey = PreferencesRepository.sortConfigPathKey(storageId, displayPath),
            config = config.toRepository(),
        )
    }

    fun folderBrowserFolderSortConfigFlow(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
    ): Flow<FolderBrowserUiState.FileSortConfig> {
        return preferencesRepository.folderBrowserFolderSortConfigForPath(
            PreferencesRepository.sortConfigPathKey(storageId, displayPath),
        ).map { it.toFolderBrowserUiState() }
    }

    fun folderBrowserFileSortConfigFlow(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
    ): Flow<FolderBrowserUiState.FileSortConfig> {
        return preferencesRepository.folderBrowserFileSortConfigForPath(
            PreferencesRepository.sortConfigPathKey(storageId, displayPath),
        ).map { it.toFolderBrowserUiState() }
    }

    suspend fun saveFolderBrowserFolderSortConfig(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
        config: FolderBrowserUiState.FileSortConfig,
    ) {
        preferencesRepository.saveFolderBrowserFolderSortConfigForPath(
            pathKey = PreferencesRepository.sortConfigPathKey(storageId, displayPath),
            config = config.toRepository(),
        )
    }

    suspend fun saveFolderBrowserFileSortConfig(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
        config: FolderBrowserUiState.FileSortConfig,
    ) {
        preferencesRepository.saveFolderBrowserFileSortConfigForPath(
            pathKey = PreferencesRepository.sortConfigPathKey(storageId, displayPath),
            config = config.toRepository(),
        )
    }

    fun externalPickerSortConfigFlow(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
    ): Flow<FileBrowserUiState.FileSortConfig> {
        return preferencesRepository.externalPickerSortConfigForPath(
            PreferencesRepository.sortConfigPathKey(storageId, displayPath),
        ).map { it.toFileBrowserUiState() }
    }

    suspend fun saveExternalPickerSortConfig(
        preferencesRepository: PreferencesRepository,
        storageId: StorageId,
        displayPath: String?,
        config: FileBrowserUiState.FileSortConfig,
    ) {
        preferencesRepository.saveExternalPickerSortConfigForPath(
            pathKey = PreferencesRepository.sortConfigPathKey(storageId, displayPath),
            config = config.toRepository(),
        )
    }

    private fun PreferencesRepository.FileSortConfig.toFileBrowserUiState(): FileBrowserUiState.FileSortConfig {
        return FileBrowserUiState.FileSortConfig(
            key = when (key) {
                PreferencesRepository.FileSortKey.Name -> FileBrowserUiState.FileSortKey.Name
                PreferencesRepository.FileSortKey.Date -> FileBrowserUiState.FileSortKey.Date
                PreferencesRepository.FileSortKey.Size -> FileBrowserUiState.FileSortKey.Size
            },
            isAscending = isAscending,
        )
    }

    private fun FileBrowserUiState.FileSortConfig.toRepository(): PreferencesRepository.FileSortConfig {
        return PreferencesRepository.FileSortConfig(
            key = when (key) {
                FileBrowserUiState.FileSortKey.Name -> PreferencesRepository.FileSortKey.Name
                FileBrowserUiState.FileSortKey.Date -> PreferencesRepository.FileSortKey.Date
                FileBrowserUiState.FileSortKey.Size -> PreferencesRepository.FileSortKey.Size
            },
            isAscending = isAscending,
        )
    }

    private fun PreferencesRepository.FileSortConfig.toFolderBrowserUiState(): FolderBrowserUiState.FileSortConfig {
        return FolderBrowserUiState.FileSortConfig(
            key = when (key) {
                PreferencesRepository.FileSortKey.Name -> FolderBrowserUiState.FileSortKey.Name
                PreferencesRepository.FileSortKey.Date -> FolderBrowserUiState.FileSortKey.Date
                PreferencesRepository.FileSortKey.Size -> FolderBrowserUiState.FileSortKey.Size
            },
            isAscending = isAscending,
        )
    }

    private fun FolderBrowserUiState.FileSortConfig.toRepository(): PreferencesRepository.FileSortConfig {
        return PreferencesRepository.FileSortConfig(
            key = when (key) {
                FolderBrowserUiState.FileSortKey.Name -> PreferencesRepository.FileSortKey.Name
                FolderBrowserUiState.FileSortKey.Date -> PreferencesRepository.FileSortKey.Date
                FolderBrowserUiState.FileSortKey.Size -> PreferencesRepository.FileSortKey.Size
            },
            isAscending = isAscending,
        )
    }
}
