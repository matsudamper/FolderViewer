package net.matsudamper.folderviewer.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.browser.DisplayConfigDropDownMenu
import net.matsudamper.folderviewer.ui.browser.UiDisplayConfig
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults
import net.matsudamper.folderviewer.ui.util.formatBytes

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
            FolderBrowserTopBar(
                title = uiState.title,
                isFavorite = uiState.isFavorite,
                visibleFavoriteButton = uiState.visibleFavoriteButton,
                onBack = uiState.callbacks::onBack,
                folderSortConfig = uiState.folderSortConfig,
                onFolderSortConfigChange = uiState.callbacks::onFolderSortConfigChanged,
                fileSortConfig = uiState.fileSortConfig,
                onFileSortConfigChange = uiState.callbacks::onFileSortConfigChanged,
                displayConfig = uiState.displayConfig,
                onDisplayConfigChange = uiState.callbacks::onDisplayModeChanged,
                onFavoriteClick = uiState.callbacks::onFavoriteClick,
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

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderBrowserTopBar(
    title: String,
    isFavorite: Boolean,
    visibleFavoriteButton: Boolean,
    onBack: () -> Unit,
    folderSortConfig: FolderBrowserUiState.FileSortConfig,
    onFolderSortConfigChange: (FolderBrowserUiState.FileSortConfig) -> Unit,
    fileSortConfig: FolderBrowserUiState.FileSortConfig,
    onFileSortConfigChange: (FolderBrowserUiState.FileSortConfig) -> Unit,
    displayConfig: UiDisplayConfig,
    onDisplayConfigChange: (UiDisplayConfig) -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(title) {
        snapshotFlow { scrollState.maxValue }
            .collect { maxValue ->
                scrollState.scrollTo(maxValue)
            }
    }

    TopAppBar(
        modifier = modifier,
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = {
            Text(
                modifier = Modifier.horizontalScroll(scrollState),
                text = title,
                maxLines = 1,
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
            if (visibleFavoriteButton) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        painter = painterResource(
                            id = if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border,
                        ),
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
            }

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
            if (showSortMenu) {
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    var selectedTab by remember { mutableIntStateOf(0) }
                    Column(modifier = Modifier.width(200.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SortTabItem(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.folder),
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                            )
                            SortTabItem(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.file),
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                            )
                        }
                        HorizontalDivider()

                        val currentConfig = if (selectedTab == 0) folderSortConfig else fileSortConfig
                        val onConfigChange = if (selectedTab == 0) onFolderSortConfigChange else onFileSortConfigChange

                        SortMenuItems(
                            config = currentConfig,
                            onConfigChange = {
                                onConfigChange(it)
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SortTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun SortMenuItems(
    config: FolderBrowserUiState.FileSortConfig,
    onConfigChange: (FolderBrowserUiState.FileSortConfig) -> Unit,
) {
    Column {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_name)) },
            onClick = { onConfigChange(config.copy(key = FolderBrowserUiState.FileSortKey.Name)) },
            leadingIcon = {
                if (config.key == FolderBrowserUiState.FileSortKey.Name) {
                    Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null)
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_date)) },
            onClick = { onConfigChange(config.copy(key = FolderBrowserUiState.FileSortKey.Date)) },
            leadingIcon = {
                if (config.key == FolderBrowserUiState.FileSortKey.Date) {
                    Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null)
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_size)) },
            onClick = { onConfigChange(config.copy(key = FolderBrowserUiState.FileSortKey.Size)) },
            leadingIcon = {
                if (config.key == FolderBrowserUiState.FileSortKey.Size) {
                    Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null)
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_asc)) },
            onClick = { onConfigChange(config.copy(isAscending = true)) },
            leadingIcon = {
                if (config.isAscending) {
                    Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null)
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_desc)) },
            onClick = { onConfigChange(config.copy(isAscending = false)) },
            leadingIcon = {
                if (!config.isAscending) {
                    Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null)
                }
            },
        )
    }
}

@Composable
private fun FolderBrowserContent(
    uiState: FolderBrowserUiState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (uiState.displayConfig.displayMode) {
        UiDisplayConfig.DisplayMode.List -> {
            FolderBrowserList(
                uiState = uiState,
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }

        UiDisplayConfig.DisplayMode.Grid -> {
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
                    stickyHeader(key = "header_${item.title}") {
                        HeaderItem(path = item.title)
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
        UiDisplayConfig.DisplaySize.Small -> 60.dp
        UiDisplayConfig.DisplaySize.Medium -> 120.dp
        UiDisplayConfig.DisplaySize.Large -> 240.dp
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
                        key = "header_${item.title}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        HeaderItem(path = item.title)
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
    displaySize: UiDisplayConfig.DisplaySize,
    modifier: Modifier = Modifier,
) {
    val size = when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> 40.dp
        UiDisplayConfig.DisplaySize.Medium -> 64.dp
        UiDisplayConfig.DisplaySize.Large -> 100.dp
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
    displaySize: UiDisplayConfig.DisplaySize,
    modifier: Modifier = Modifier,
) {
    val padding = when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> 4.dp
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
                when (painter.state) {
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
