package net.matsudamper.folderviewer.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadErrorDetailScreen(uiState: UploadErrorDetailUiState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.upload_error_detail_title))
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
                            text = stringResource(R.string.upload_error_detail_upload_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            InfoCard(
                title = stringResource(R.string.upload_error_detail_file_info),
            ) {
                InfoRow(
                    label = stringResource(R.string.upload_error_detail_name),
                    value = uiState.name,
                )
                InfoRow(
                    label = stringResource(R.string.upload_error_detail_type),
                    value = if (uiState.isFolder) {
                        stringResource(R.string.upload_error_detail_type_folder)
                    } else {
                        stringResource(R.string.upload_error_detail_type_file)
                    },
                )
                InfoRow(
                    label = stringResource(R.string.upload_error_detail_storage),
                    value = uiState.storageName,
                )
                InfoRow(
                    label = stringResource(R.string.upload_error_detail_path),
                    value = uiState.displayPath,
                )
            }

            InfoCard(
                title = stringResource(R.string.upload_error_detail_error_info),
            ) {
                if (uiState.errorMessage != null) {
                    InfoRow(
                        label = stringResource(R.string.upload_error_detail_error_message),
                        value = uiState.errorMessage,
                    )
                }
                if (uiState.errorCause != null) {
                    InfoRow(
                        label = stringResource(R.string.upload_error_detail_error_cause),
                        value = uiState.errorCause,
                    )
                }
                if (uiState.errorMessage == null && uiState.errorCause == null) {
                    Text(
                        text = stringResource(R.string.upload_error_detail_no_error_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

private val previewCallbacks = object : UploadErrorDetailUiState.Callbacks {
    override fun onBackClick() = Unit
}

@Preview(showBackground = true)
@Composable
private fun UploadErrorDetailScreenPreview() {
    UploadErrorDetailScreen(
        uiState = UploadErrorDetailUiState(
            name = "test_file.txt",
            isFolder = false,
            displayPath = "/storage/smb/documents",
            storageName = "NAS",
            errorMessage = "Connection timed out",
            errorCause = "java.net.SocketTimeoutException: connect timed out",
            callbacks = previewCallbacks,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun UploadErrorDetailScreenFolderPreview() {
    UploadErrorDetailScreen(
        uiState = UploadErrorDetailUiState(
            name = "photos",
            isFolder = true,
            displayPath = "/storage/smb/backup",
            storageName = "SharePoint",
            errorMessage = null,
            errorCause = null,
            callbacks = previewCallbacks,
        ),
    )
}
