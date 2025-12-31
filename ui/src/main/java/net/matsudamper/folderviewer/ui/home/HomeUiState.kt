package net.matsudamper.folderviewer.ui.home

import net.matsudamper.folderviewer.repository.StorageConfiguration

data class HomeUiState(
    val storages: List<StorageConfiguration> = emptyList(),
)
