package net.matsudamper.folderviewer.viewmodel.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.ui.browser.ImageViewerUiState

@HiltViewModel(assistedFactory = ImageViewerViewModel.Companion.Factory::class)
class ImageViewerViewModel @AssistedInject constructor(
    @Assisted private val args: ImageViewer,
) : ViewModel() {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                currentIndex = args.allPaths.indexOf(args.fileId).coerceAtLeast(0),
            ),
        )

    val uiState: StateFlow<ImageViewerUiState> =
        MutableStateFlow(
            ImageViewerUiState(
                images = args.allPaths.map { fileId ->
                    ImageViewerUiState.ImageItem(
                        title = fileId.id.substringAfterLast('/').substringAfterLast('\\'),
                        imageSource = FileImageSource.Original(
                            storageId = args.storageId,
                            fileId = fileId,
                        ),
                    )
                },
                currentIndex = viewModelStateFlow.value.currentIndex,
                callbacks = object : ImageViewerUiState.Callbacks {
                    override fun onBack() {
                        viewModelScope.launch {
                            viewModelEventChannel.send(ViewModelEvent.PopBackStack)
                        }
                    }

                    override fun onImageChanged(index: Int) {
                        viewModelStateFlow.update { it.copy(currentIndex = index) }
                    }
                },
            ),
        ).also { mutableUiState ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    mutableUiState.update {
                        it.copy(
                            currentIndex = viewModelState.currentIndex,
                        )
                    }
                }
            }
        }.asStateFlow()

    private data class ViewModelState(
        val currentIndex: Int,
    )

    sealed interface ViewModelEvent {
        data object PopBackStack : ViewModelEvent
    }

    companion object {
        @AssistedFactory
        interface Factory {
            fun create(arguments: ImageViewer): ImageViewerViewModel
        }
    }
}
