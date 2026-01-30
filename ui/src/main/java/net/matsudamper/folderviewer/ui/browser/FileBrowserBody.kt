package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserBody(
    uiState: FileBrowserUiState,
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
            FileBrowserUiState.ContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            FileBrowserUiState.ContentState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.error_loading_files),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text(text = stringResource(R.string.reload))
                        }
                    }
                }
            }

            FileBrowserUiState.ContentState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_files),
                    )
                }
            }

            is FileBrowserUiState.ContentState.Content -> {
                FileBrowserContent(
                    content = contentState,
                    displayConfig = uiState.displayConfig,
                    isSelectionMode = isSelectionMode,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun FileBrowserContent(
    content: FileBrowserUiState.ContentState.Content,
    displayConfig: UiDisplayConfig,
    isSelectionMode: Boolean,
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
                            FileBrowserGridItem(
                                file = item,
                                displaySize = displayConfig.displaySize,
                                textOverflow = TextOverflow.Ellipsis,
                                isSelectionMode = isSelectionMode,
                            )
                        }
                    }
                }
                if (content.favorites.isNotEmpty()) {
                    stickyHeader { FileHeaderItem(title = stringResource(R.string.favorites)) }
                    items(
                        items = content.favorites,
                    ) { item ->
                        FileBrowserGridItem(
                            file = item,
                            displaySize = displayConfig.displaySize,
                            textOverflow = TextOverflow.StartEllipsis,
                            isSelectionMode = isSelectionMode,
                        )
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
                            FileBrowserListItem(
                                file = item,
                                displaySize = displayConfig.displaySize,
                                textOverflow = TextOverflow.Ellipsis,
                                isSelectionMode = isSelectionMode,
                            )
                        }
                    }
                }
                if (content.favorites.isNotEmpty()) {
                    stickyHeader { FileHeaderItem(title = stringResource(R.string.favorites)) }
                    items(
                        items = content.favorites,
                    ) { item ->
                        FileBrowserListItem(
                            file = item,
                            displaySize = displayConfig.displaySize,
                            textOverflow = TextOverflow.StartEllipsis,
                            isSelectionMode = isSelectionMode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileBrowserGridItem(
    file: FileBrowserUiState.UiFileItem.File,
    displaySize: UiDisplayConfig.DisplaySize,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
) {
    when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> {
            FileSmallGridItem(
                file = file,
                textOverflow = textOverflow,
                isSelectionMode = isSelectionMode,
            )
        }

        UiDisplayConfig.DisplaySize.Medium -> {
            FileLargeGridItem(
                file = file,
                textOverflow = textOverflow,
                isSelectionMode = isSelectionMode,
            )
        }

        UiDisplayConfig.DisplaySize.Large -> {
            FileLargeGridItem(
                file = file,
                textOverflow = textOverflow,
                isSelectionMode = isSelectionMode,
            )
        }
    }
}

@Composable
private fun FileBrowserListItem(
    file: FileBrowserUiState.UiFileItem.File,
    displaySize: UiDisplayConfig.DisplaySize,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
) {
    when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> {
            FileSmallListItem(
                file = file,
                textOverflow = textOverflow,
                isSelectionMode = isSelectionMode,
            )
        }

        UiDisplayConfig.DisplaySize.Medium -> {
            FileMediumListItem(
                file = file,
                textOverflow = textOverflow,
                isSelectionMode = isSelectionMode,
            )
        }

        UiDisplayConfig.DisplaySize.Large -> {
            FileLargeListItem(
                file = file,
                textOverflow = textOverflow,
                isSelectionMode = isSelectionMode,
            )
        }
    }
}
