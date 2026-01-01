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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserTopBar(
    currentPath: String,
    onBack: () -> Unit,
    onUpClick: () -> Unit,
    modifier: Modifier = Modifier,
    sortConfig: FileSortConfig? = null,
    onSortConfigChanged: ((FileSortConfig) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(currentPath) {
        snapshotFlow { scrollState.maxValue }
            .collect { maxValue ->
                scrollState.scrollTo(maxValue)
            }
    }

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                modifier = Modifier.horizontalScroll(scrollState),
                text = currentPath.ifEmpty { stringResource(R.string.root) },
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = if (currentPath.isEmpty()) onBack else onUpClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            if (sortConfig != null && onSortConfigChanged != null) {
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
                            onSortConfigChanged(sortConfig.copy(key = FileSortKey.Name))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.key == FileSortKey.Name) {
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
                            onSortConfigChanged(sortConfig.copy(key = FileSortKey.Date))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.key == FileSortKey.Date) {
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
                            onSortConfigChanged(sortConfig.copy(key = FileSortKey.Size))
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortConfig.key == FileSortKey.Size) {
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
                            onSortConfigChanged(sortConfig.copy(isAscending = true))
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
                            onSortConfigChanged(sortConfig.copy(isAscending = false))
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
