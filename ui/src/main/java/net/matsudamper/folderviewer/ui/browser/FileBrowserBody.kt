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
import coil.ImageLoader
import net.matsudamper.folderviewer.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserBody(
    uiState: FileBrowserUiState,
    imageLoader: ImageLoader,
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
                when (uiState.displayMode) {
                    DisplayMode.Grid -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                        ) {
                            items(
                                items = uiState.files,
                                key = { it.path },
                            ) { file ->
                                FileGridItem(
                                    file = file,
                                    imageLoader = imageLoader,
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                        ) {
                            items(
                                items = uiState.files,
                                key = { it.path },
                            ) { file ->
                                when (uiState.displayMode) {
                                    DisplayMode.Small -> {
                                        FileSmallListItem(
                                            file = file,
                                            imageLoader = imageLoader,
                                        )
                                    }

                                    DisplayMode.Medium -> {
                                        FileListItem(
                                            file = file,
                                            imageLoader = imageLoader,
                                        )
                                    }

                                    DisplayMode.Grid -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
