package net.matsudamper.folderviewer.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.flow.Flow
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.browser.FileBrowserTopBar
import net.matsudamper.folderviewer.ui.util.formatBytes
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState as CommonFileBrowserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(
    uiState: FolderBrowserUiState,
    uiEvent: Flow<FolderBrowserUiEvent>,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        uiState.callbacks.onBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiEvent) {
        uiEvent.collect { event ->
            when (event) {
                is FolderBrowserUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FileBrowserTopBar(
                title = uiState.title,
                onBack = uiState.callbacks::onBack,
                sortConfig = CommonFileBrowserUiState.FileSortConfig(
                    key = when (uiState.sortConfig.key) {
                        FolderBrowserUiState.FileSortKey.Name -> CommonFileBrowserUiState.FileSortKey.Name
                        FolderBrowserUiState.FileSortKey.Date -> CommonFileBrowserUiState.FileSortKey.Date
                        FolderBrowserUiState.FileSortKey.Size -> CommonFileBrowserUiState.FileSortKey.Size
                    },
                    isAscending = uiState.sortConfig.isAscending,
                ),
                onSortConfigChange = { config ->
                    uiState.callbacks.onSortConfigChanged(
                        FolderBrowserUiState.FileSortConfig(
                            key = when (config.key) {
                                CommonFileBrowserUiState.FileSortKey.Name -> FolderBrowserUiState.FileSortKey.Name
                                CommonFileBrowserUiState.FileSortKey.Date -> FolderBrowserUiState.FileSortKey.Date
                                CommonFileBrowserUiState.FileSortKey.Size -> FolderBrowserUiState.FileSortKey.Size
                            },
                            isAscending = config.isAscending,
                        ),
                    )
                },
                displayConfig = CommonFileBrowserUiState.DisplayConfig(
                    displayMode = when (uiState.displayConfig.displayMode) {
                        FolderBrowserUiState.DisplayMode.List -> CommonFileBrowserUiState.DisplayMode.List
                        FolderBrowserUiState.DisplayMode.Grid -> CommonFileBrowserUiState.DisplayMode.Grid
                    },
                    displaySize = when (uiState.displayConfig.displaySize) {
                        FolderBrowserUiState.DisplaySize.Small -> CommonFileBrowserUiState.DisplaySize.Small
                        FolderBrowserUiState.DisplaySize.Medium -> CommonFileBrowserUiState.DisplaySize.Medium
                        FolderBrowserUiState.DisplaySize.Large -> CommonFileBrowserUiState.DisplaySize.Large
                    },
                ),
                onDisplayConfigChange = { config ->
                    uiState.callbacks.onDisplayModeChanged(
                        FolderBrowserUiState.DisplayConfig(
                            displayMode = when (config.displayMode) {
                                CommonFileBrowserUiState.DisplayMode.List -> FolderBrowserUiState.DisplayMode.List
                                CommonFileBrowserUiState.DisplayMode.Grid -> FolderBrowserUiState.DisplayMode.Grid
                            },
                            displaySize = when (config.displaySize) {
                                CommonFileBrowserUiState.DisplaySize.Small -> FolderBrowserUiState.DisplaySize.Small
                                CommonFileBrowserUiState.DisplaySize.Medium -> FolderBrowserUiState.DisplaySize.Medium
                                CommonFileBrowserUiState.DisplaySize.Large -> FolderBrowserUiState.DisplaySize.Large
                            },
                        ),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            isRefreshing = uiState.isRefreshing,
            onRefresh = uiState.callbacks::onRefresh,
            state = pullToRefreshState,
        ) {
            if (uiState.isLoading && uiState.files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                FolderBrowserContent(
                    uiState = uiState,
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderBrowserContent(
    uiState: FolderBrowserUiState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (uiState.displayConfig.displayMode) {
        FolderBrowserUiState.DisplayMode.List -> {
            FolderBrowserList(
                uiState = uiState,
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }

        FolderBrowserUiState.DisplayMode.Grid -> {
            FolderBrowserGrid(
                uiState = uiState,
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderBrowserList(
    uiState: FolderBrowserUiState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        uiState.files.forEach { item ->
            when (item) {
                is FolderBrowserUiState.UiFileItem.Header -> {
                    stickyHeader(key = "header_${item.path}") {
                        HeaderItem(path = item.path)
                    }
                }

                is FolderBrowserUiState.UiFileItem.File -> {
                    item(key = item.path) {
                        FileListItem(
                            file = item,
                            displaySize = uiState.displayConfig.displaySize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderBrowserGrid(
    uiState: FolderBrowserUiState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val minSize = when (uiState.displayConfig.displaySize) {
        FolderBrowserUiState.DisplaySize.Small -> 60.dp
        FolderBrowserUiState.DisplaySize.Medium -> 120.dp
        FolderBrowserUiState.DisplaySize.Large -> 240.dp
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        uiState.files.forEach { item ->
            when (item) {
                is FolderBrowserUiState.UiFileItem.Header -> {
                    item(
                        key = "header_${item.path}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        HeaderItem(path = item.path)
                    }
                }

                is FolderBrowserUiState.UiFileItem.File -> {
                    item(key = item.path) {
                        FileGridItem(
                            file = item,
                            displaySize = uiState.displayConfig.displaySize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderItem(
    path: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = path,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileListItem(
    file: FolderBrowserUiState.UiFileItem.File,
    displaySize: FolderBrowserUiState.DisplaySize,
    modifier: Modifier = Modifier,
) {
    val size = when (displaySize) {
        FolderBrowserUiState.DisplaySize.Small -> 40.dp
        FolderBrowserUiState.DisplaySize.Medium -> 64.dp
        FolderBrowserUiState.DisplaySize.Large -> 100.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = file.callbacks::onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(
            file = file,
            modifier = Modifier
                .size(size)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!file.isDirectory) {
                Text(
                    text = formatBytes(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FileGridItem(
    file: FolderBrowserUiState.UiFileItem.File,
    displaySize: FolderBrowserUiState.DisplaySize,
    modifier: Modifier = Modifier,
) {
    val padding = when (displaySize) {
        FolderBrowserUiState.DisplaySize.Small -> 4.dp
        else -> 8.dp
    }

    Column(
        modifier = modifier
            .clickable(onClick = file.callbacks::onClick)
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileIcon(
            file = file,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FileIcon(
    file: FolderBrowserUiState.UiFileItem.File,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val imageSource = file.thumbnail
        if (imageSource != null) {
            SubcomposeAsyncImage(
                model = imageSource,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            ) {
                when (val state = painter.state) {
                    is AsyncImagePainter.State.Loading,
                    is AsyncImagePainter.State.Error,
                    -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_file),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            tint = LocalContentColor.current,
                        )
                    }

                    else -> {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
        } else {
            Icon(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(
                    id = if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file,
                ),
                contentDescription = null,
            )
        }
    }
}
