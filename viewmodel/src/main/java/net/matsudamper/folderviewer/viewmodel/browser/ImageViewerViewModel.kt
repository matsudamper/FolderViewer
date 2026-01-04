package net.matsudamper.folderviewer.viewmodel.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.ui.browser.ImageViewerUiState

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<ImageViewer>()

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                currentIndex = args.allPaths.indexOf(args.path).coerceAtLeast(0),
            ),
        )

    val uiState: StateFlow<ImageViewerUiState> =
        MutableStateFlow(
            ImageViewerUiState(
                images = args.allPaths.map { path ->
                    ImageViewerUiState.ImageItem(
                        title = path.substringAfterLast('/').substringAfterLast('\\'),
                        imageSource = FileImageSource.Original(
                            storageId = args.id,
                            path = path,
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
}
