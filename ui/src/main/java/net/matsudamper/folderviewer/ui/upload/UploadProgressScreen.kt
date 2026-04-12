package net.matsudamper.folderviewer.ui.upload

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@Composable
public fun UploadProgressScreen(
    uiState: UploadProgressUiState,
    modifier: Modifier = Modifier,
) {
    if (uiState.showClearConfirmDialog) {
        ClearHistoryConfirmDialog(
            onConfirm = { uiState.callbacks.onClearHistoryConfirm() },
            onDismiss = { uiState.callbacks.onClearHistoryDismiss() },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            UploadProgressTopBar(
                onBack = { uiState.callbacks.onBackClick() },
                onClearHistory = { uiState.callbacks.onClearHistoryClick() },
            )
        },
    ) { innerPadding ->
        if (uiState.uploadItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_uploads),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = uiState.uploadItems,
                    key = { it.id },
                ) { item ->
                    when (item) {
                        is UploadProgressUiState.UploadItem.Paste -> {
                            PasteItemRow(
                                item = item,
                                onClick = { uiState.callbacks.onItemClick(item) },
                            )
                        }
                        is UploadProgressUiState.UploadItem.Delete -> {
                            DeleteItemRow(
                                item = item,
                                onClick = { uiState.callbacks.onItemClick(item) },
                            )
                        }
                        else -> {
                            UploadItemRow(
                                item = item,
                                onClick = { uiState.callbacks.onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadItemRow(
    item: UploadProgressUiState.UploadItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (item.canNavigate) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(
                id = when (item) {
                    is UploadProgressUiState.UploadItem.File -> R.drawable.ic_file
                    is UploadProgressUiState.UploadItem.Folder -> R.drawable.ic_folder
                    is UploadProgressUiState.UploadItem.Paste -> R.drawable.ic_file
                    is UploadProgressUiState.UploadItem.Delete -> R.drawable.ic_file
                },
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        when (item.state) {
            UploadProgressUiState.UploadState.RUNNING -> {
                val progress = item.progress
                val indicatorModifier = Modifier
                    .padding(4.dp)
                    .size(24.dp)
                if (progress != null) {
                    CircularWavyProgressIndicator(
                        modifier = indicatorModifier,
                        progress = { progress },
                    )
                } else {
                    CircularWavyProgressIndicator(
                        modifier = indicatorModifier,
                    )
                }
            }

            UploadProgressUiState.UploadState.SUCCEEDED -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            UploadProgressUiState.UploadState.FAILED -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasteItemRow(
    item: UploadProgressUiState.UploadItem.Paste,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (item.canNavigate) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_file),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            when {
                item.isPausable -> {
                    IconButton(onClick = { item.pasteCallbacks.onPauseClick() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pause),
                            contentDescription = stringResource(R.string.paste_pause),
                        )
                    }
                }
                item.isResumable -> {
                    IconButton(onClick = { item.pasteCallbacks.onResumeClick() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_play),
                            contentDescription = stringResource(R.string.paste_resume),
                        )
                    }
                }
                item.state == UploadProgressUiState.UploadState.SUCCEEDED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                item.state == UploadProgressUiState.UploadState.FAILED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        val overallProgress = item.progress
        if (overallProgress != null) {
            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val currentFileProgress = item.currentFileProgress
        if (currentFileProgress != null && item.state == UploadProgressUiState.UploadState.RUNNING) {
            LinearProgressIndicator(
                progress = { currentFileProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteItemRow(
    item: UploadProgressUiState.UploadItem.Delete,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (item.canNavigate) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_file),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            when (item.state) {
                UploadProgressUiState.UploadState.RUNNING -> {
                    val progress = item.progress
                    val indicatorModifier = Modifier
                        .padding(4.dp)
                        .size(24.dp)
                    if (progress != null) {
                        CircularWavyProgressIndicator(
                            modifier = indicatorModifier,
                            progress = { progress },
                        )
                    } else {
                        CircularWavyProgressIndicator(
                            modifier = indicatorModifier,
                        )
                    }
                }
                UploadProgressUiState.UploadState.SUCCEEDED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                UploadProgressUiState.UploadState.FAILED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {}
            }
        }

        val overallProgress = item.progress
        if (overallProgress != null) {
            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadProgressTopBar(
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = {
            Text(
                text = stringResource(R.string.upload_progress),
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
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert),
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.clear_upload_history)) },
                    onClick = {
                        showMenu = false
                        onClearHistory()
                    },
                )
            }
        },
    )
}

@Composable
private fun ClearHistoryConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_upload_history_confirm_title)) },
        text = { Text(stringResource(R.string.clear_upload_history_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
