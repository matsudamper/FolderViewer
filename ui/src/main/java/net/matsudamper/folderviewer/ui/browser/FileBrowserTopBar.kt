package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sortConfig: FileBrowserUiState.FileSortConfig? = null,
    onSortConfigChange: ((FileBrowserUiState.FileSortConfig) -> Unit)? = null,
    displayMode: FileBrowserUiState.DisplayMode = FileBrowserUiState.DisplayMode.Medium,
    onDisplayModeChange: ((FileBrowserUiState.DisplayMode) -> Unit)? = null,
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
            if (onDisplayModeChange != null) {
                var showDisplayMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showDisplayMenu = true }) {
                    Icon(
                        painter = painterResource(
                            id = if (displayMode == FileBrowserUiState.DisplayMode.Grid) {
                                R.drawable.ic_grid_view
                            } else {
                                R.drawable.ic_view_list
                            },
                        ),
                        contentDescription = stringResource(R.string.display_mode),
                    )
                }
                DropdownMenu(
                    expanded = showDisplayMenu,
                    onDismissRequest = { showDisplayMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.display_mode_small)) },
                        onClick = {
                            onDisplayModeChange(FileBrowserUiState.DisplayMode.Small)
                            showDisplayMenu = false
                        },
                        leadingIcon = {
                            if (displayMode == FileBrowserUiState.DisplayMode.Small) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.display_mode_medium)) },
                        onClick = {
                            onDisplayModeChange(FileBrowserUiState.DisplayMode.Medium)
                            showDisplayMenu = false
                        },
                        leadingIcon = {
                            if (displayMode == FileBrowserUiState.DisplayMode.Medium) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.display_mode_grid)) },
                        onClick = {
                            onDisplayModeChange(FileBrowserUiState.DisplayMode.Grid)
                            showDisplayMenu = false
                        },
                        leadingIcon = {
                            if (displayMode == FileBrowserUiState.DisplayMode.Grid) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }

            if (sortConfig != null && onSortConfigChange != null) {
                var showSortMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sort),
                        contentDescription = stringResource(R.string.sort_by),
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_name)) },
                        onClick = {
                            onSortConfigChange(sortConfig.copy(key = FileBrowserUiState.FileSortKey.Name))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.key == FileBrowserUiState.FileSortKey.Name) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_date)) },
                        onClick = {
                            onSortConfigChange(sortConfig.copy(key = FileBrowserUiState.FileSortKey.Date))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.key == FileBrowserUiState.FileSortKey.Date) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_size)) },
                        onClick = {
                            onSortConfigChange(sortConfig.copy(key = FileBrowserUiState.FileSortKey.Size))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.key == FileBrowserUiState.FileSortKey.Size) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_asc)) },
                        onClick = {
                            onSortConfigChange(sortConfig.copy(isAscending = true))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.isAscending) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_desc)) },
                        onClick = {
                            onSortConfigChange(sortConfig.copy(isAscending = false))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (!sortConfig.isAscending) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
        },
    )
}
