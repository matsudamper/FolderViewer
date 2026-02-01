package net.matsudamper.folderviewer.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDetailScreen(uiState: UploadDetailUiState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.upload_detail_title))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (uiState.uploadStatus) {
                UploadDetailUiState.UploadStatus.UPLOADING -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    text = stringResource(R.string.upload_detail_uploading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (uiState.progressText != null) {
                                Text(
                                    text = "${stringResource(R.string.upload_detail_overall_progress)}: ${uiState.progressText}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                UploadDetailUiState.UploadStatus.SUCCEEDED -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Row {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.upload_detail_upload_succeeded),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                UploadDetailUiState.UploadStatus.FAILED -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Row {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = stringResource(R.string.upload_detail_upload_failed),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            InfoCard(
                title = stringResource(R.string.upload_detail_file_info),
            ) {
                InfoRow(
                    label = stringResource(R.string.upload_detail_name),
                    value = uiState.name,
                )
                InfoRow(
                    label = stringResource(R.string.upload_detail_type),
                    value = if (uiState.isFolder) {
                        stringResource(R.string.upload_detail_type_folder)
                    } else {
                        stringResource(R.string.upload_detail_type_file)
                    },
                )
                InfoRow(
                    label = stringResource(R.string.upload_detail_storage),
                    value = uiState.storageName,
                )
                InfoRow(
                    label = stringResource(R.string.upload_detail_path),
                    value = uiState.displayPath,
                )
            }

            if (uiState.currentUploadFile != null) {
                InfoCard(
                    title = stringResource(R.string.upload_detail_current_file),
                ) {
                    Text(
                        text = uiState.currentUploadFile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (uiState.currentUploadFile.progress != null) {
                        LinearProgressIndicator(
                            progress = { uiState.currentUploadFile.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (uiState.currentUploadFile.progressText != null) {
                        Text(
                            text = uiState.currentUploadFile.progressText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.uploadStatus == UploadDetailUiState.UploadStatus.FAILED) {
                InfoCard(
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
                            text = stringResource(R.string.upload_detail_no_error_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = { uiState.callbacks.onNavigateToDirectoryClick() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.upload_detail_navigate_to_directory))
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val previewCallbacks = object : UploadDetailUiState.Callbacks {
    override fun onBackClick() = Unit
    override fun onNavigateToDirectoryClick() = Unit
}

@Preview(showBackground = true)
@Composable
private fun UploadDetailScreenUploadingPreview() {
    UploadDetailScreen(
        uiState = UploadDetailUiState(
            name = "test_file.txt",
            isFolder = false,
            displayPath = "/storage/smb/documents",
            storageName = "NAS",
            uploadStatus = UploadDetailUiState.UploadStatus.UPLOADING,
            errorMessage = null,
            errorCause = null,
            progressText = null,
            currentUploadFile = null,
            callbacks = previewCallbacks,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun UploadDetailScreenFolderUploadingPreview() {
    UploadDetailScreen(
        uiState = UploadDetailUiState(
            name = "photos",
            isFolder = true,
            displayPath = "/storage/smb/backup",
            storageName = "NAS",
            uploadStatus = UploadDetailUiState.UploadStatus.UPLOADING,
            errorMessage = null,
            errorCause = null,
            progressText = "50.0MB/200.0MB",
            currentUploadFile = UploadDetailUiState.CurrentUploadFile(
                name = "photo2.jpg",
                progressText = "25.0MB/75.0MB",
                progress = 0.33f,
            ),
            callbacks = previewCallbacks,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun UploadDetailScreenErrorPreview() {
    UploadDetailScreen(
        uiState = UploadDetailUiState(
            name = "test_file.txt",
            isFolder = false,
            displayPath = "/storage/smb/documents",
            storageName = "NAS",
            uploadStatus = UploadDetailUiState.UploadStatus.FAILED,
            errorMessage = "Connection timed out",
            errorCause = "java.net.SocketTimeoutException: connect timed out",
            progressText = null,
            currentUploadFile = null,
            callbacks = previewCallbacks,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun UploadDetailScreenSuccessPreview() {
    UploadDetailScreen(
        uiState = UploadDetailUiState(
            name = "photos",
            isFolder = true,
            displayPath = "/storage/smb/backup",
            storageName = "SharePoint",
            uploadStatus = UploadDetailUiState.UploadStatus.SUCCEEDED,
            errorMessage = null,
            errorCause = null,
            progressText = null,
            currentUploadFile = null,
            callbacks = previewCallbacks,
        ),
    )
}
