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
import androidx.compose.material3.LinearProgressIndicator
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
public fun ExtractDetailScreen(
    uiState: ExtractDetailUiState,
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
                            text = stringResource(R.string.extract_detail_title),
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
            if (uiState.status == ExtractDetailUiState.Status.RUNNING) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            if (uiState.progress != null) {
                                LinearProgressIndicator(
                                    progress = { uiState.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(
                                text = uiState.progressText ?: uiState.statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            if (uiState.status == ExtractDetailUiState.Status.FAILED) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.extract_detail_failed),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(start = 8.dp),
                            )
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
                    title = stringResource(R.string.extract_detail_info),
                ) {
                    InfoRow(
                        label = stringResource(R.string.extract_detail_source_file),
                        value = uiState.sourceFile,
                    )
                    InfoRow(
                        label = stringResource(R.string.extract_detail_output_name),
                        value = uiState.outputName,
                    )
                    if (uiState.outputPath != null) {
                        InfoRow(
                            label = stringResource(R.string.extract_detail_output_path),
                            value = uiState.outputPath,
                        )
                    }
                    InfoRow(
                        label = stringResource(R.string.extract_detail_type),
                        value = uiState.extractTypeLabel,
                    )
                }
            }

            if (uiState.canNavigateToOutput) {
                item {
                    Button(
                        onClick = { uiState.callbacks.onNavigateToOutputClick() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.extract_detail_navigate_to_output))
                    }
                }
            }

            if (uiState.canOpenOutputFile) {
                item {
                    Button(
                        onClick = { uiState.callbacks.onOpenOutputFileClick() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.extract_detail_open_output_file))
                    }
                }
            }
        }
    }
}

private val previewExtractCallbacks = object : ExtractDetailUiState.Callbacks {
    override fun onBackClick() = Unit
    override fun onNavigateToOutputClick() = Unit
    override fun onOpenOutputFileClick() = Unit
}

@Preview(showBackground = true)
@Composable
private fun ExtractDetailScreenDuplicateFilePreview() {
    MaterialTheme {
        ExtractDetailScreen(
            uiState = ExtractDetailUiState(
                jobName = "app.apk.xzを展開",
                statusText = "失敗",
                status = ExtractDetailUiState.Status.FAILED,
                sourceFile = "/sdcard/Download/app.apk.xz",
                outputName = "app",
                outputPath = "/sdcard/Download/app.apk",
                canNavigateToOutput = true,
                canOpenOutputFile = true,
                extractTypeLabel = "XZ（.xz）",
                errorMessage = "同じ名前のファイルが既に存在します: app.apk",
                errorCause = null,
                progress = null,
                progressText = null,
                callbacks = previewExtractCallbacks,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExtractDetailScreenPreview() {
    MaterialTheme {
        ExtractDetailScreen(
            uiState = ExtractDetailUiState(
                jobName = "archive.zipを展開",
                statusText = "失敗",
                status = ExtractDetailUiState.Status.FAILED,
                sourceFile = "/sdcard/Download/archive.zip",
                outputName = "archive",
                outputPath = "/sdcard/Download/archive",
                canNavigateToOutput = true,
                canOpenOutputFile = false,
                extractTypeLabel = "ZIP（フォルダ）",
                errorMessage = "同じ名前のフォルダが既に存在します: archive",
                errorCause = "java.lang.IllegalStateException",
                progress = null,
                progressText = null,
                callbacks = previewExtractCallbacks,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExtractDetailScreenRunningPreview() {
    MaterialTheme {
        ExtractDetailScreen(
            uiState = ExtractDetailUiState(
                jobName = "archive.zipを展開",
                statusText = "解凍中",
                status = ExtractDetailUiState.Status.RUNNING,
                sourceFile = "/sdcard/Download/archive.zip",
                outputName = "archive",
                outputPath = "/sdcard/Download/archive",
                canNavigateToOutput = false,
                canOpenOutputFile = false,
                extractTypeLabel = "ZIP（フォルダ）",
                errorMessage = null,
                errorCause = null,
                progress = 0.42f,
                progressText = "42/100 ファイル",
                callbacks = previewExtractCallbacks,
            ),
        )
    }
}
