package net.matsudamper.folderviewer.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R

enum class OperationFileStatus {
    COMPLETED,
    PENDING,
    FAILED,
}

@Immutable
data class OperationFileRow(
    val key: String,
    val fileName: String,
    val sourcePath: String?,
    val destinationPath: String,
    val status: OperationFileStatus,
    val errorMessage: String?,
)

@Immutable
data class OperationFileFilter(
    val showCompleted: Boolean,
    val showPending: Boolean,
    val showFailed: Boolean,
    val onToggleCompleted: () -> Unit,
    val onTogglePending: () -> Unit,
    val onToggleFailed: () -> Unit,
)

internal fun LazyListScope.operationFileList(
    files: List<OperationFileRow>,
    filter: OperationFileFilter,
) {
    val completedCount = files.count { it.status == OperationFileStatus.COMPLETED }
    val pendingCount = files.count { it.status == OperationFileStatus.PENDING }
    val failedCount = files.count { it.status == OperationFileStatus.FAILED }

    item(key = "operation_file_filter") {
        OperationFileFilterRow(
            modifier = Modifier.fillMaxWidth(),
            filter = filter,
            completedCount = completedCount,
            pendingCount = pendingCount,
            failedCount = failedCount,
        )
    }

    val visible = files.filter { row ->
        when (row.status) {
            OperationFileStatus.COMPLETED -> filter.showCompleted
            OperationFileStatus.PENDING -> filter.showPending
            OperationFileStatus.FAILED -> filter.showFailed
        }
    }

    if (visible.isEmpty()) {
        item(key = "operation_file_empty") {
            Text(
                text = stringResource(R.string.operation_file_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    } else {
        items(
            count = visible.size,
            key = { index -> "operation_file_${visible[index].key}" },
        ) { index ->
            OperationFileRowItem(
                modifier = Modifier.fillMaxWidth(),
                row = visible[index],
            )
        }
    }
}

@Composable
internal fun OperationFileListColumn(
    files: List<OperationFileRow>,
    filter: OperationFileFilter,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OperationFileFilterRow(
            modifier = Modifier.fillMaxWidth(),
            filter = filter,
            completedCount = files.count { it.status == OperationFileStatus.COMPLETED },
            pendingCount = files.count { it.status == OperationFileStatus.PENDING },
            failedCount = files.count { it.status == OperationFileStatus.FAILED },
        )
        val visible = files.filter { row ->
            when (row.status) {
                OperationFileStatus.COMPLETED -> filter.showCompleted
                OperationFileStatus.PENDING -> filter.showPending
                OperationFileStatus.FAILED -> filter.showFailed
            }
        }
        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.operation_file_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visible.forEach { row ->
                OperationFileRowItem(
                    modifier = Modifier.fillMaxWidth(),
                    row = row,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OperationFileFilterRow(
    filter: OperationFileFilter,
    completedCount: Int,
    pendingCount: Int,
    failedCount: Int,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter.showCompleted,
            onClick = { filter.onToggleCompleted() },
            label = {
                Text(stringResource(R.string.operation_file_filter_completed, completedCount))
            },
        )
        FilterChip(
            selected = filter.showPending,
            onClick = { filter.onTogglePending() },
            label = {
                Text(stringResource(R.string.operation_file_filter_pending, pendingCount))
            },
        )
        FilterChip(
            selected = filter.showFailed,
            onClick = { filter.onToggleFailed() },
            label = {
                Text(stringResource(R.string.operation_file_filter_failed, failedCount))
            },
        )
    }
}

@Composable
private fun OperationFileRowItem(
    row: OperationFileRow,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (row.status) {
        OperationFileStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        OperationFileStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
        OperationFileStatus.PENDING -> MaterialTheme.colorScheme.surface
    }
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            val statusText = when (row.status) {
                OperationFileStatus.COMPLETED -> stringResource(R.string.operation_file_status_completed)
                OperationFileStatus.PENDING -> stringResource(R.string.operation_file_status_pending)
                OperationFileStatus.FAILED -> stringResource(R.string.operation_file_status_failed)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = when (row.status) {
                    OperationFileStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            if (row.sourcePath != null) {
                Text(
                    text = stringResource(R.string.operation_file_source_format, row.sourcePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (row.sourcePath != null) {
                    stringResource(R.string.operation_file_destination_format, row.destinationPath)
                } else {
                    row.destinationPath
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.errorMessage != null) {
                Text(
                    text = row.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
