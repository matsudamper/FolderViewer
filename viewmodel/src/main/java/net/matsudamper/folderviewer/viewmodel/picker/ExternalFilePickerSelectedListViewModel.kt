package net.matsudamper.folderviewer.viewmodel.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.repository.ExternalPickerRepository
import net.matsudamper.folderviewer.repository.StorageRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.picker.ExternalFilePickerSelectedListUiState
import net.matsudamper.folderviewer.viewmodel.util.FileUtil

@HiltViewModel
class ExternalFilePickerSelectedListViewModel @Inject constructor(
    private val externalPickerRepository: ExternalPickerRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow: Flow<ViewModelEvent> = viewModelEventChannel.receiveAsFlow()

    private val callbacks = object : ExternalFilePickerSelectedListUiState.Callbacks {
        override fun onBack() {
            viewModelScope.launch {
                viewModelEventChannel.send(ViewModelEvent.PopBackStack)
            }
        }
    }

    val uiState = externalPickerRepository.selectedItems
        .map { selectedItems ->
            ExternalFilePickerSelectedListUiState(
                items = selectedItems.values.map { pickerItem ->
                    val isImage = FileUtil.isImage(pickerItem.name)
                    val isVideo = FileUtil.isVideo(pickerItem.name)
                    val isPreviewable = isImage || isVideo
                    ExternalFilePickerSelectedListUiState.Item(
                        fileId = pickerItem.id,
                        name = pickerItem.name,
                        thumbnail = if (isImage) FileImageSource.Thumbnail(fileId = pickerItem.id) else null,
                        isPreviewable = isPreviewable,
                        callbacks = object : ExternalFilePickerSelectedListUiState.Item.ItemCallbacks {
                            override fun onRemove() {
                                externalPickerRepository.removeItem(pickerItem.id)
                            }

                            override fun onTap() {
                                if (!isPreviewable) return
                                viewModelScope.launch {
                                    if (isImage) {
                                        val allImages = externalPickerRepository.selectedItems.value.values
                                            .filter { FileUtil.isImage(it.name) }
                                            .map { it.id }
                                        viewModelEventChannel.send(
                                            ViewModelEvent.NavigateToImageViewer(
                                                fileId = pickerItem.id,
                                                allPaths = allImages,
                                            ),
                                        )
                                    } else {
                                        openWithExternalPlayer(pickerItem)
                                    }
                                }
                            }
                        },
                    )
                },
                callbacks = callbacks,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExternalFilePickerSelectedListUiState(
                items = emptyList(),
                callbacks = callbacks,
            ),
        )

    private suspend fun openWithExternalPlayer(pickerItem: ExternalPickerRepository.PickerFileItem) {
        runCatching {
            val repo = storageRepository.getFileRepository(pickerItem.id.storageId) ?: return
            val viewSourceUri = repo.getViewSourceUri(pickerItem.id)
            val mimeType = FileUtil.getMimeType(pickerItem.name)
            viewModelEventChannel.send(
                ViewModelEvent.OpenWithExternalPlayer(
                    viewSourceUri = viewSourceUri,
                    fileId = pickerItem.id,
                    fileName = pickerItem.name,
                    mimeType = mimeType,
                ),
            )
        }.onFailure { e ->
            e.printStackTrace()
        }
    }

    sealed interface ViewModelEvent {
        data object PopBackStack : ViewModelEvent
        data class NavigateToImageViewer(
            val fileId: FileObjectId.Item,
            val allPaths: List<FileObjectId.Item>,
        ) : ViewModelEvent
        data class OpenWithExternalPlayer(
            val viewSourceUri: ViewSourceUri,
            val fileId: FileObjectId.Item,
            val fileName: String,
            val mimeType: String?,
        ) : ViewModelEvent
    }
}
