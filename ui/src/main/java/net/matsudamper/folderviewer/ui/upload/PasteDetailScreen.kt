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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import net.matsudamper.folderviewer.ui.upload.PasteDetailUiState.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PasteDetailScreen(
    uiState: PasteDetailUiState,
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
                            text = stringResource(R.string.paste_detail_title),
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
        bottomBar = {
            if (uiState.duplicateFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = { uiState.callbacks.onApplyResolutions() },
                        enabled = uiState.canApply,
                    ) {
                        Text(stringResource(R.string.paste_detail_apply_resolutions))
                    }
                }
            }
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
            if (uiState.status == Status.FAILED) {
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
                                    text = stringResource(R.string.paste_detail_failed),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
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
                        if (uiState.errorMessage == null && uiState.errorCause == null) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.upload_detail_no_error_info),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (uiState.failedFiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        text = stringResource(R.string.paste_detail_failed_files_section) +
                            " (${uiState.failedFiles.size})",
                    )
                }
                items(
                    items = uiState.failedFiles,
                    key = { it.path },
                ) { item ->
                    PasteFailedFileRow(item = item)
                }
            }

            item {
                SectionHeader(
                    text = stringResource(R.string.paste_detail_duplicate_files_section) +
                        " (${uiState.duplicateFiles.size})",
                )
            }

            if (uiState.duplicateFiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.paste_detail_no_duplicates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(
                    items = uiState.duplicateFiles,
                    key = { it.fileId },
                ) { item ->
                    DuplicateFileCard(item = item)
                }
            }

            item {
                SectionHeader(
                    text = stringResource(R.string.paste_detail_completed_files_section) +
                        " (${uiState.completedFiles.size})",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(
                items = uiState.completedFiles,
                key = { it.path },
            ) { item ->
                CompletedFileRow(item = item)
            }
        }
    }
}

@Composable
private fun PasteFailedFileRow(
    item: PasteDetailUiState.FailedFileItem,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(
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
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun DuplicateFileCard(
    item: PasteDetailUiState.DuplicateFileItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.resolution != null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FileInfoColumn(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.paste_detail_source_label),
                    path = item.sourcePath,
                    size = item.sourceSizeText,
                )
                FileInfoColumn(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.paste_detail_destination_label),
                    path = item.destinationPath,
                    size = item.destinationSizeText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isKeepSelected = item.resolution == PasteDetailUiState.Resolution.KEEP_DESTINATION
                val isOverwriteSelected = item.resolution == PasteDetailUiState.Resolution.OVERWRITE_WITH_SOURCE

                if (isKeepSelected) {
                    FilledTonalButton(
                        onClick = { item.onKeepDestination() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.paste_detail_keep_destination))
                    }
                } else {
                    OutlinedButton(
                        onClick = { item.onKeepDestination() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.paste_detail_keep_destination))
                    }
                }

                if (isOverwriteSelected) {
                    FilledTonalButton(
                        onClick = { item.onOverwriteWithSource() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.paste_detail_overwrite_with_source))
                    }
                } else {
                    OutlinedButton(
                        onClick = { item.onOverwriteWithSource() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.paste_detail_overwrite_with_source))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoColumn(
    label: String,
    path: String,
    size: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            text = size,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CompletedFileRow(
    item: PasteDetailUiState.CompletedFileItem,
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
                text = "${item.path} · ${item.sizeText}",
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
