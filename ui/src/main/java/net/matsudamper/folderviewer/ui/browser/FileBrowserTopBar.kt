package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserTopBar(
    title: String,
    onBack: () -> Unit,
    sortConfig: FileBrowserUiState.FileSortConfig,
    onSortConfigChange: (FileBrowserUiState.FileSortConfig) -> Unit,
    displayConfig: FileBrowserUiState.DisplayConfig,
    onDisplayConfigChange: (FileBrowserUiState.DisplayConfig) -> Unit,
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
            var showDisplayMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showDisplayMenu = true }) {
                Icon(
                    painter = painterResource(
                        id = if (displayConfig.displayMode == FileBrowserUiState.DisplayMode.Grid) {
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
        },
    )
}
