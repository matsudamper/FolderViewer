package net.matsudamper.folderviewer.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import dagger.hilt.android.scopes.ActivityRetainedScoped
import jakarta.inject.Inject
import net.matsudamper.folderviewer.common.FileObjectId

@ActivityRetainedScoped
class ExternalPickerRepository @Inject constructor() {
    data class PickerFileItem(
        val id: FileObjectId.Item,
        val name: String,
        val size: Long,
        val lastModified: Long,
    )

    private val _selectedItems: MutableStateFlow<LinkedHashMap<FileObjectId.Item, PickerFileItem>> =
        MutableStateFlow(LinkedHashMap())
    val selectedItems: StateFlow<Map<FileObjectId.Item, PickerFileItem>> = _selectedItems.asStateFlow()

    fun toggleItem(item: PickerFileItem) {
        _selectedItems.update { current ->
            val next = LinkedHashMap(current)
            if (next.containsKey(item.id)) {
                next.remove(item.id)
            } else {
                next[item.id] = item
            }
            next
        }
    }

    fun removeItem(id: FileObjectId.Item) {
        _selectedItems.update { current ->
            val next = LinkedHashMap(current)
            next.remove(id)
            next
        }
    }

    fun isSelected(id: FileObjectId.Item): Boolean {
        return _selectedItems.value.containsKey(id)
    }

    fun clear() {
        _selectedItems.update { LinkedHashMap() }
    }
}
