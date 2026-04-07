package net.matsudamper.folderviewer.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DeleteDetailScreen(
    uiState: DeleteDetailUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = MyTopAppBarDefaults.topAppBarColors(),
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.delete_detail_title),
                            maxLines = 1,
                        )
                        Text(
                            text = uiState.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { uiState.callbacks.onBackClick() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
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
            if (uiState.status == DeleteDetailUiState.Status.FAILED) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = stringResource(R.string.delete_detail_failed),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                if (uiState.errorMessage != null || uiState.errorCause != null) {
                    item {
                        InfoCard(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(R.string.upload_detail_error_info),
                        ) {
                            if (uiState.errorMessage != null) {
                                InfoRow(
                                    label = stringResource(R.string.upload_detail_error_message),
                                    value = uiState.errorMessage,
                                )
                            }
                            if (uiState.errorCause != null) {
                                InfoRow(
                                    label = stringResource(R.string.upload_detail_error_cause),
                                    value = uiState.errorCause,
                                )
                            }
                        }
                    }
                }
            }

            item {
                InfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.delete_detail_info),
                ) {
                    InfoRow(
                        label = stringResource(R.string.delete_detail_total_files),
                        value = "${uiState.totalFiles}",
                    )
                    InfoRow(
                        label = stringResource(R.string.delete_detail_completed_files),
                        value = "${uiState.completedFiles}",
                    )
                    if (uiState.failedFiles > 0) {
                        InfoRow(
                            label = stringResource(R.string.delete_detail_failed_files),
                            value = "${uiState.failedFiles}",
                        )
                    }
                }
            }

            if (uiState.failedFileItems.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.delete_detail_failed_section) +
                            " (${uiState.failedFileItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(
                    items = uiState.failedFileItems,
                    key = { it.path },
                ) { item ->
                    FailedFileRow(item = item)
                }
            }

            if (uiState.completedFileItems.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.delete_detail_completed_section) +
                            " (${uiState.completedFileItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 0.dp),
                    )
                }
                items(
                    items = uiState.completedFileItems,
                    key = { it.path },
                ) { item ->
                    DeletedFileRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun FailedFileRow(
    item: DeleteDetailUiState.FailedFileItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
            )
            Text(
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
            )
            Text(
                text = item.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DeletedFileRow(
    item: DeleteDetailUiState.CompletedFileItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_file),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
