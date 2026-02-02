package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.ui.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserScreenContent(
    uiState: FileBrowserUiState,
    snackbarHostState: SnackbarHostState,
    showCreateDirectoryDialog: Boolean = false,
    onCreateDirectoryDialogDismiss: () -> Unit = {},
    onConfirmCreateDirectory: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = uiState.isSelectionMode) {
        uiState.callbacks.onCancelSelection()
    }
    val callbacks = uiState.callbacks
    val containerColor = MaterialTheme.colorScheme.background
    var expanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = {
            if (uiState.isSelectionMode) {
                FileBrowserSelectionTopBar(
                    selectedCount = uiState.selectedCount,
                    onCancelSelection = callbacks::onCancelSelection,
                )
            } else {
                FileBrowserTopBar(
                    title = uiState.title,
                    isFavorite = uiState.isFavorite,
                    visibleFavoriteButton = uiState.visibleFavoriteButton,
                    onBack = callbacks::onBack,
                    sortConfig = uiState.sortConfig,
                    onSortConfigChange = callbacks::onSortConfigChanged,
                    displayConfig = uiState.displayConfig,
                    onDisplayConfigChange = callbacks::onDisplayModeChanged,
                    onFavoriteClick = callbacks::onFavoriteClick,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.visibleFolderBrowserButton) {
                HorizontalFloatingToolbar(
                    expanded = expanded,
                    floatingActionButton = {
                        FloatingToolbarDefaults.VibrantFloatingActionButton(
                            onClick = { expanded = !expanded },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = "メニュー",
                            )
                        }
                    },
                ) {
                    IconButton(onClick = { callbacks.onFolderBrowserClick() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder_eye),
                            contentDescription = "フォルダビューアを開く",
                        )
                    }
                    IconButton(onClick = { callbacks.onUploadFileClick() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_upload_file),
                            contentDescription = "ファイルをアップロード",
                        )
                    }
                    IconButton(onClick = { callbacks.onUploadFolderClick() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_upload_file),
                            contentDescription = "フォルダをアップロード",
                        )
                    }
                    IconButton(onClick = { callbacks.onCreateDirectoryClick() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder_add),
                            contentDescription = "ディレクトリを作成",
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        FileBrowserBody(
            modifier = Modifier
                .fillMaxSize(),
            uiState = uiState,
            onRefresh = callbacks::onRefresh,
            contentPadding = innerPadding,
        )
    }

    if (showCreateDirectoryDialog) {
        var createDirectoryInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                onCreateDirectoryDialogDismiss()
            },
            title = { Text("ディレクトリを作成") },
            text = {
                TextField(
                    value = createDirectoryInput,
                    onValueChange = { createDirectoryInput = it },
                    label = { Text("ディレクトリ名") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createDirectoryInput.isNotBlank()) {
                            onConfirmCreateDirectory(createDirectoryInput)
                        }
                    },
                    enabled = createDirectoryInput.isNotBlank(),
                ) {
                    Text("作成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onCreateDirectoryDialogDismiss()
                    },
                ) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
@Preview
private fun Preview() {
    FileBrowserScreenContent(
        uiState = FileBrowserUiState(
            visibleFolderBrowserButton = true,
            visibleFavoriteButton = true,
            isRefreshing = false,
            title = "サンプルフォルダ",
            isFavorite = false,
            sortConfig = FileBrowserUiState.FileSortConfig(
                key = FileBrowserUiState.FileSortKey.Name,
                isAscending = true,
            ),
            displayConfig = UiDisplayConfig(
                displayMode = UiDisplayConfig.DisplayMode.List,
                displaySize = UiDisplayConfig.DisplaySize.Medium,
            ),
            isSelectionMode = false,
            selectedCount = 0,
            callbacks = object : FileBrowserUiState.Callbacks {
                override fun onRefresh() = Unit
                override fun onBack() = Unit
                override fun onSortConfigChanged(config: FileBrowserUiState.FileSortConfig) = Unit
                override fun onDisplayModeChanged(config: UiDisplayConfig) = Unit
                override fun onFolderBrowserClick() = Unit
                override fun onFavoriteClick() = Unit
                override fun onUploadFileClick() = Unit
                override fun onUploadFolderClick() = Unit
                override fun onCreateDirectoryClick() = Unit
                override fun onConfirmCreateDirectory(directoryName: String) = Unit
                override fun onCancelSelection() = Unit
            },
            contentState = FileBrowserUiState.ContentState.Content(
                files = listOf(
                    FileBrowserUiState.UiFileItem.File(
                        name = "ドキュメント",
                        key = "doc1",
                        isDirectory = true,
                        size = 0,
                        lastModified = System.currentTimeMillis(),
                        thumbnail = null,
                        isSelected = false,
                        callbacks = object : FileBrowserUiState.UiFileItem.File.Callbacks {
                            override fun onClick() = Unit
                            override fun onLongClick() = Unit
                            override fun onCheckedChange(checked: Boolean) = Unit
                        },
                    ),
                    FileBrowserUiState.UiFileItem.File(
                        name = "sample.txt",
                        key = "file1",
                        isDirectory = false,
                        size = 1024,
                        lastModified = System.currentTimeMillis(),
                        thumbnail = null,
                        isSelected = false,
                        callbacks = object : FileBrowserUiState.UiFileItem.File.Callbacks {
                            override fun onClick() = Unit
                            override fun onLongClick() = Unit
                            override fun onCheckedChange(checked: Boolean) = Unit
                        },
                    ),
                ),
                favorites = emptyList(),
            ),
        ),
        snackbarHostState = SnackbarHostState(),
    )
}
