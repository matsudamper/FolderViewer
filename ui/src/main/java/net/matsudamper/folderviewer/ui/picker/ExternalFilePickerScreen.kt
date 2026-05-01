package net.matsudamper.folderviewer.ui.picker

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarHorizontalFabPosition
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.browser.DisplayConfigDropDownMenu
import net.matsudamper.folderviewer.ui.browser.FileHeaderItem
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.browser.FileLargeGridItem
import net.matsudamper.folderviewer.ui.browser.FileLargeListItem
import net.matsudamper.folderviewer.ui.browser.FileMediumListItem
import net.matsudamper.folderviewer.ui.browser.FileSmallGridItem
import net.matsudamper.folderviewer.ui.browser.FileSmallListItem
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults
import net.matsudamper.folderviewer.ui.util.formatBytes
import net.matsudamper.folderviewer.ui.util.plus

@Composable
fun ExternalFilePickerScreen(
    uiState: ExternalFilePickerUiState,
    uiEvent: Flow<ExternalFilePickerUiEvent>,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        uiState.callbacks.onBack()
    }

    var propertiesDialogState by remember { mutableStateOf<ExternalFilePickerUiEvent.ShowFilePropertiesDialog?>(null) }

    LaunchedEffect(uiEvent) {
        uiEvent.collect { event ->
            when (event) {
                is ExternalFilePickerUiEvent.ShowFilePropertiesDialog -> {
                    propertiesDialogState = event
                }
            }
        }
    }

    ExternalFilePickerScreenContent(
        uiState = uiState,
        modifier = modifier,
    )

    val dialog = propertiesDialogState
    if (dialog != null) {
        FilePropertiesDialog(
            event = dialog,
            onDismiss = { propertiesDialogState = null },
            onPreview = {
                val d = propertiesDialogState ?: return@FilePropertiesDialog
                propertiesDialogState = null
                d.callbacks.onPreview()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExternalFilePickerScreenContent(
    uiState: ExternalFilePickerUiState,
    modifier: Modifier = Modifier,
) {
    val callbacks = uiState.callbacks
    var fabHeight by remember { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExternalFilePickerTopBar(
                title = uiState.title,
                onBack = callbacks::onBack,
                sortConfig = uiState.sortConfig,
                onSortConfigChange = callbacks::onSortConfigChanged,
                displayConfig = uiState.displayConfig,
                onDisplayConfigChange = callbacks::onDisplayModeChanged,
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (uiState.isMultipleMode) {
                HorizontalFloatingToolbar(
                    modifier = Modifier.onSizeChanged { fabHeight = it.height },
                    floatingActionButtonPosition = FloatingToolbarHorizontalFabPosition.End,
                    expanded = true,
                    contentPadding = PaddingValues(0.dp),
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    floatingActionButton = {
                        FloatingToolbarDefaults.VibrantFloatingActionButton(
                            onClick = callbacks::onSubmit,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.external_picker_submit),
                            )
                        }
                    },
                ) {
                    TextButton(onClick = callbacks::onSelectedCountClick) {
                        Text(text = uiState.selectedCount.toString())
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        },
    ) { innerPadding ->
        val contentPadding = if (uiState.isMultipleMode && fabHeight > 0) {
            innerPadding.plus(
                PaddingValues(bottom = with(LocalDensity.current) { fabHeight.toDp() } + 16.dp),
            )
        } else {
            innerPadding
        }
        ExternalFilePickerBody(
            modifier = Modifier.fillMaxSize(),
            uiState = uiState,
            onRefresh = callbacks::onRefresh,
            contentPadding = contentPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalFilePickerTopBar(
    title: String,
    onBack: () -> Unit,
    sortConfig: FileBrowserUiState.FileSortConfig,
    onSortConfigChange: (FileBrowserUiState.FileSortConfig) -> Unit,
    displayConfig: UiDisplayConfig,
    onDisplayConfigChange: (UiDisplayConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            var showDisplayMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showDisplayMenu = true }) {
                Icon(
                    painter = painterResource(
                        id = if (displayConfig.displayMode == UiDisplayConfig.DisplayMode.Grid) {
                            R.drawable.ic_grid_view
                        } else {
                            R.drawable.ic_view_list
                        },
                    ),
                    contentDescription = stringResource(R.string.display_mode),
                )
            }
            DisplayConfigDropDownMenu(
                expanded = showDisplayMenu,
                onDismissRequest = { showDisplayMenu = false },
                displayConfig = displayConfig,
                onDisplayConfigChange = onDisplayConfigChange,
            )

            var showSortMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showSortMenu = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sort),
                    contentDescription = stringResource(R.string.sort_by),
                )
            }
            net.matsudamper.folderviewer.ui.browser.FileBrowserSortDropDownMenu(
                showSortMenu = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                sortConfig = sortConfig,
                onSortConfigChange = onSortConfigChange,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExternalFilePickerBody(
    uiState: ExternalFilePickerUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            Indicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(contentPadding),
                isRefreshing = uiState.isRefreshing,
                state = state,
            )
        },
    ) {
        when (val contentState = uiState.contentState) {
            ExternalFilePickerUiState.ContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator()
                }
            }

            ExternalFilePickerUiState.ContentState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(R.string.error_loading_files))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text(text = stringResource(R.string.reload))
                        }
                    }
                }
            }

            ExternalFilePickerUiState.ContentState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.no_files))
                }
            }

            is ExternalFilePickerUiState.ContentState.Content -> {
                ExternalFilePickerContent(
                    content = contentState,
                    displayConfig = uiState.displayConfig,
                    isMultipleMode = uiState.isMultipleMode,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun ExternalFilePickerContent(
    content: ExternalFilePickerUiState.ContentState.Content,
    displayConfig: UiDisplayConfig,
    isMultipleMode: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (displayConfig.displayMode) {
        UiDisplayConfig.DisplayMode.Grid -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(
                    minSize = when (displayConfig.displaySize) {
                        UiDisplayConfig.DisplaySize.Small -> 60.dp
                        UiDisplayConfig.DisplaySize.Medium -> 120.dp
                        UiDisplayConfig.DisplaySize.Large -> 240.dp
                    },
                ),
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(
                    items = content.files,
                    key = { item ->
                        when (item) {
                            is FileBrowserUiState.UiFileItem.Header -> "header_${item.title}"
                            is FileBrowserUiState.UiFileItem.File -> item.key
                        }
                    },
                    contentType = {
                        when (it) {
                            is FileBrowserUiState.UiFileItem.Header -> "Header"
                            is FileBrowserUiState.UiFileItem.File -> displayConfig.displaySize
                        }
                    },
                    span = { item ->
                        if (item is FileBrowserUiState.UiFileItem.Header) {
                            GridItemSpan(maxLineSpan)
                        } else {
                            GridItemSpan(1)
                        }
                    },
                ) { item ->
                    when (item) {
                        is FileBrowserUiState.UiFileItem.Header -> {
                            FileHeaderItem(title = item.title)
                        }

                        is FileBrowserUiState.UiFileItem.File -> {
                            PickerGridItem(
                                file = item,
                                displaySize = displayConfig.displaySize,
                                isMultipleMode = isMultipleMode,
                            )
                        }
                    }
                }
            }
        }

        UiDisplayConfig.DisplayMode.List -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(
                    items = content.files,
                    key = { item ->
                        when (item) {
                            is FileBrowserUiState.UiFileItem.Header -> "header_${item.title}"
                            is FileBrowserUiState.UiFileItem.File -> item.key
                        }
                    },
                ) { item ->
                    when (item) {
                        is FileBrowserUiState.UiFileItem.Header -> {
                            FileHeaderItem(title = item.title)
                        }

                        is FileBrowserUiState.UiFileItem.File -> {
                            PickerListItem(
                                file = item,
                                displaySize = displayConfig.displaySize,
                                isMultipleMode = isMultipleMode,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerGridItem(
    file: FileBrowserUiState.UiFileItem.File,
    displaySize: UiDisplayConfig.DisplaySize,
    isMultipleMode: Boolean,
) {
    val showCheckbox = isMultipleMode && !file.isDirectory
    when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> FileSmallGridItem(
            file = file,
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = showCheckbox,
            isPasteMode = false,
        )

        UiDisplayConfig.DisplaySize.Medium,
        UiDisplayConfig.DisplaySize.Large,
        -> FileLargeGridItem(
            file = file,
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = showCheckbox,
            isPasteMode = false,
        )
    }
}

@Composable
private fun PickerListItem(
    file: FileBrowserUiState.UiFileItem.File,
    displaySize: UiDisplayConfig.DisplaySize,
    isMultipleMode: Boolean,
) {
    val showCheckbox = isMultipleMode && !file.isDirectory
    when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> FileSmallListItem(
            file = file,
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = showCheckbox,
            isPasteMode = false,
        )

        UiDisplayConfig.DisplaySize.Medium -> FileMediumListItem(
            file = file,
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = showCheckbox,
            isPasteMode = false,
        )

        UiDisplayConfig.DisplaySize.Large -> FileLargeListItem(
            file = file,
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = showCheckbox,
            isPasteMode = false,
        )
    }
}

@Composable
private fun FilePropertiesDialog(
    event: ExternalFilePickerUiEvent.ShowFilePropertiesDialog,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.external_picker_properties_title)) },
        text = {
            Column {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PropertiesRow(
                    label = stringResource(R.string.external_picker_file_size),
                    value = formatBytes(event.size),
                )
                Spacer(modifier = Modifier.height(4.dp))
                PropertiesRow(
                    label = stringResource(R.string.external_picker_last_modified),
                    value = dateFormatter.format(Instant.ofEpochMilli(event.lastModified)),
                )
            }
        },
        confirmButton = {
            if (event.isPreviewable) {
                TextButton(onClick = onPreview) {
                    Text(text = stringResource(R.string.external_picker_preview))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PropertiesRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
