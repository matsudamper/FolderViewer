package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.files.isEmpty() -> {
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

            else -> {
                FileBrowserContent(
                    uiState = uiState,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun FileBrowserContent(
    uiState: FileBrowserUiState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (uiState.displayConfig.displayMode) {
        UiDisplayConfig.DisplayMode.Grid -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(
                    minSize = when (uiState.displayConfig.displaySize) {
                        UiDisplayConfig.DisplaySize.Small -> 60.dp
                        UiDisplayConfig.DisplaySize.Medium -> 120.dp
                        UiDisplayConfig.DisplaySize.Large -> 240.dp
                    },
                ),
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(
                    items = uiState.files,
                    key = { it.path },
                    contentType = { uiState.displayConfig.displaySize },
                ) { file ->
                    FileBrowserGridItem(
                        file = file,
                        displaySize = uiState.displayConfig.displaySize,
                    )
                }
            }
        }

        UiDisplayConfig.DisplayMode.List -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(
                    items = uiState.files,
                    key = { it.path },
                ) { file ->
                    FileBrowserListItem(
                        file = file,
                        displaySize = uiState.displayConfig.displaySize,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileBrowserGridItem(
    file: FileBrowserUiState.UiFileItem,
    displaySize: UiDisplayConfig.DisplaySize,
) {
    when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> {
            FileSmallGridItem(
                file = file,
            )
        }

        UiDisplayConfig.DisplaySize.Medium -> {
            FileLargeGridItem(
                file = file,
            )
        }

        UiDisplayConfig.DisplaySize.Large -> {
            FileLargeGridItem(
                file = file,
            )
        }
    }
}

@Composable
private fun FileBrowserListItem(
    file: FileBrowserUiState.UiFileItem,
    displaySize: UiDisplayConfig.DisplaySize,
) {
    when (displaySize) {
        UiDisplayConfig.DisplaySize.Small -> {
            FileSmallListItem(
                file = file,
            )
        }

        UiDisplayConfig.DisplaySize.Medium -> {
            FileMediumListItem(
                file = file,
            )
        }

        UiDisplayConfig.DisplaySize.Large -> {
            FileLargeListItem(
                file = file,
            )
        }
    }
}
