package net.matsudamper.folderviewer.viewmodel.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.repository.StorageRepository

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<ImageViewer>()

    val path: String = args.path

    private val _fileRepository = MutableStateFlow<FileRepository?>(null)
    val fileRepository: StateFlow<FileRepository?> = _fileRepository.asStateFlow()

    init {
        viewModelScope.launch {
            val repo = storageRepository.getFileRepository(args.id)
            _fileRepository.value = repo
        }
    }
}
