package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserTopBar(
    title: String,
    isFavorite: Boolean,
    visibleFavoriteButton: Boolean,
    onBack: () -> Unit,
    sortConfig: FileBrowserUiState.FileSortConfig,
    onSortConfigChange: (FileBrowserUiState.FileSortConfig) -> Unit,
    displayConfig: UiDisplayConfig,
    onDisplayConfigChange: (UiDisplayConfig) -> Unit,
    onFavoriteClick: () -> Unit,
    visibleOpenFolderWithExternalAppButton: Boolean,
    onOpenFolderWithExternalAppClick: () -> Unit,
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
            var showMoreMenu by remember { mutableStateOf(false) }
            var showDisplayMenu by remember { mutableStateOf(false) }
            var showSortMenu by remember { mutableStateOf(false) }
            IconButton(onClick = {
                showDisplayMenu = false
                showSortMenu = false
                showMoreMenu = true
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                if (visibleFavoriteButton) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isFavorite) {
                                        R.string.remove_from_favorites
                                    } else {
                                        R.string.add_to_favorites
                                    },
                                ),
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onFavoriteClick()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(
                                    id = if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border,
                                ),
                                contentDescription = null,
                                tint = if (isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        },
                    )
                }
                if (visibleOpenFolderWithExternalAppButton) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_folder_with_external_app)) },
                        onClick = {
                            showMoreMenu = false
                            onOpenFolderWithExternalAppClick()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_open_in_new),
                                contentDescription = null,
                            )
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.display_mode)) },
                    onClick = {
                        showMoreMenu = false
                        showDisplayMenu = true
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(
                                id = if (displayConfig.displayMode == UiDisplayConfig.DisplayMode.Grid) {
                                    R.drawable.ic_grid_view
                                } else {
                                    R.drawable.ic_view_list
                                },
                            ),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_right),
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_by)) },
                    onClick = {
                        showMoreMenu = false
                        showSortMenu = true
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sort),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_right),
                            contentDescription = null,
                        )
                    },
                )
            }

            DisplayConfigDropDownMenu(
                expanded = showDisplayMenu,
                onDismissRequest = { showDisplayMenu = false },
                displayConfig = displayConfig,
                onDisplayConfigChange = onDisplayConfigChange,
            )

            FileBrowserSortDropDownMenu(
                showSortMenu = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                sortConfig = sortConfig,
                onSortConfigChange = onSortConfigChange,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserSelectionTopBar(
    selectedCount: Int,
    onCancelSelection: () -> Unit,
    onSelectAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = {
            Text(
                text = selectedCount.toString(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onCancelSelection) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cancel_selection),
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAllClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_select_all),
                    contentDescription = stringResource(R.string.select_all),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FileBrowserSelectionFloatingToolbar(
    visibleCompressMenu: Boolean,
    visibleExtractMenu: Boolean,
    onCopyClick: () -> Unit,
    onCutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenClick: () -> Unit,
    onCompressClick: () -> Unit,
    onExtractClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        modifier = modifier,
        expanded = true,
        contentPadding = PaddingValues(0.dp),
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        IconButton(onClick = onCopyClick) {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = stringResource(R.string.copy),
            )
        }
        IconButton(onClick = onCutClick) {
            Icon(
                painter = painterResource(R.drawable.ic_content_cut),
                contentDescription = stringResource(R.string.cut),
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.delete),
            )
        }
        IconButton(onClick = onShareClick) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.share),
            )
        }
        Box {
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open)) },
                    onClick = {
                        showMoreMenu = false
                        onOpenClick()
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_open_in_new),
                            contentDescription = null,
                        )
                    },
                )
                if (visibleExtractMenu) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.extract)) },
                        onClick = {
                            showMoreMenu = false
                            onExtractClick()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_folder_zip),
                                contentDescription = null,
                            )
                        },
                    )
                }
                if (visibleCompressMenu) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.compress)) },
                        onClick = {
                            showMoreMenu = false
                            onCompressClick()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_folder_zip),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
@Preview
private fun FileBrowserTopBarPreview() {
    FileBrowserTopBar(
        title = "フォルダ名",
        isFavorite = true,
        visibleFavoriteButton = true,
        onBack = {},
        sortConfig = FileBrowserUiState.FileSortConfig(
            key = FileBrowserUiState.FileSortKey.Name,
            isAscending = true,
        ),
        onSortConfigChange = {},
        displayConfig = UiDisplayConfig(
            displayMode = UiDisplayConfig.DisplayMode.List,
            displaySize = UiDisplayConfig.DisplaySize.Medium,
        ),
        onDisplayConfigChange = {},
        onFavoriteClick = {},
        visibleOpenFolderWithExternalAppButton = true,
        onOpenFolderWithExternalAppClick = {},
    )
}

@Composable
@Preview
private fun SelectionTopBarPreview() {
    FileBrowserSelectionTopBar(
        selectedCount = 3,
        onCancelSelection = {},
        onSelectAllClick = {},
    )
}

@Composable
@Preview
private fun SelectionFloatingToolbarPreview() {
    FileBrowserSelectionFloatingToolbar(
        visibleCompressMenu = true,
        visibleExtractMenu = true,
        onCopyClick = {},
        onCutClick = {},
        onDeleteClick = {},
        onShareClick = {},
        onOpenClick = {},
        onCompressClick = {},
        onExtractClick = {},
    )
}

@Composable
internal fun FileBrowserSortDropDownMenu(
    showSortMenu: Boolean,
    onDismissRequest: () -> Unit,
    sortConfig: FileBrowserUiState.FileSortConfig,
    onSortConfigChange: (FileBrowserUiState.FileSortConfig) -> Unit,
) {
    DropdownMenu(
        expanded = showSortMenu,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_name)) },
            onClick = {
                onSortConfigChange(sortConfig.copy(key = FileBrowserUiState.FileSortKey.Name))
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
