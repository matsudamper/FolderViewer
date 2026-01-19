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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    Scaffold(
        modifier = modifier,
        topBar = {
            UploadProgressTopBar(
                onBack = { uiState.callbacks.onBackClick() },
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
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = uiState.uploadItems,
                    key = { it.id },
                ) { item ->
                    UploadItemRow(
                        item = item,
                        onClick = { uiState.callbacks.onItemClick(item) },
                    )
                }
            }
        }
    }
}

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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(
                id = when (item) {
                    is UploadProgressUiState.UploadItem.File -> R.drawable.ic_file
                    is UploadProgressUiState.UploadItem.Folder -> R.drawable.ic_folder
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
                text = when (item.state) {
                    UploadProgressUiState.UploadState.ENQUEUED -> stringResource(R.string.upload_state_enqueued)
                    UploadProgressUiState.UploadState.RUNNING -> stringResource(R.string.upload_state_running)
                    UploadProgressUiState.UploadState.SUCCEEDED -> stringResource(R.string.upload_state_succeeded)
                    UploadProgressUiState.UploadState.FAILED -> stringResource(R.string.upload_state_failed)
                    UploadProgressUiState.UploadState.CANCELLED -> stringResource(R.string.upload_state_cancelled)
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (item.state) {
                    UploadProgressUiState.UploadState.SUCCEEDED -> MaterialTheme.colorScheme.primary
                    UploadProgressUiState.UploadState.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        when (item.state) {
            UploadProgressUiState.UploadState.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp,
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadProgressTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    )
}
