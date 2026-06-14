package net.matsudamper.folderviewer.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
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
        bottomBar = {
            if (uiState.canRetry) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = { uiState.callbacks.onRetryClick() }) {
                        Text(stringResource(R.string.operation_detail_retry))
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

            item {
                Text(
                    text = stringResource(R.string.operation_file_list_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            operationFileList(files = uiState.files, filter = uiState.fileFilter)
        }
    }
}

private val previewDeleteCallbacks = object : DeleteDetailUiState.Callbacks {
    override fun onBackClick() = Unit
    override fun onRetryClick() = Unit
}

@Preview(showBackground = true)
@Composable
private fun DeleteDetailScreenPreview() {
    MaterialTheme {
        DeleteDetailScreen(
            uiState = DeleteDetailUiState(
                jobName = "3件を削除",
                statusText = "失敗",
                status = DeleteDetailUiState.Status.FAILED,
                totalFiles = 3,
                completedFiles = 1,
                failedFiles = 1,
                errorMessage = "1 件のファイルが失敗しました",
                errorCause = null,
                files = listOf(
                    OperationFileRow(
                        key = "1",
                        fileName = "photo.jpg",
                        sourcePath = null,
                        destinationPath = "/sdcard/DCIM/photo.jpg",
                        status = OperationFileStatus.COMPLETED,
                        errorMessage = null,
                    ),
                    OperationFileRow(
                        key = "2",
                        fileName = "document.pdf",
                        sourcePath = null,
                        destinationPath = "/sdcard/Downloads/document.pdf",
                        status = OperationFileStatus.FAILED,
                        errorMessage = "アクセスできません",
                    ),
                    OperationFileRow(
                        key = "3",
                        fileName = "movie.mp4",
                        sourcePath = null,
                        destinationPath = "/sdcard/Movies/movie.mp4",
                        status = OperationFileStatus.PENDING,
                        errorMessage = null,
                    ),
                ),
                fileFilter = OperationFileFilter(
                    showCompleted = true,
                    showPending = true,
                    showFailed = true,
                    onToggleCompleted = {},
                    onTogglePending = {},
                    onToggleFailed = {},
                ),
                canRetry = true,
                callbacks = previewDeleteCallbacks,
            ),
        )
    }
}
