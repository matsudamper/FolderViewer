package net.matsudamper.folderviewer.viewmodel.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.ui.browser.ImageViewerUiState

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<ImageViewer>()

    private val _event = Channel<Event>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private val callbacks = object : ImageViewerUiState.Callbacks {
        override fun onBack() {
            viewModelScope.launch {
                _event.send(Event.PopBackStack)
            }
        }
    }

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                imageSource = FileImageSource.Original(
                    storageId = args.id,
                    path = args.path,
                ),
            ),
        )

    val uiState: StateFlow<ImageViewerUiState> =
        MutableStateFlow(
            ImageViewerUiState(
                imageSource = FileImageSource.Original(
                    storageId = args.id,
                    path = args.path,
                ),
                callbacks = callbacks,
            ),
        ).also { mutableUiState ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    mutableUiState.update {
                        it.copy(
                            imageSource = viewModelState.imageSource,
                        )
                    }
                }
            }
        }.asStateFlow()

    private data class ViewModelState(
        val imageSource: FileImageSource.Original,
    )

    sealed interface Event {
        data object PopBackStack : Event
    }
}
