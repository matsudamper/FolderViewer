package net.matsudamper.folderviewer.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private fun insertLineBreakOpportunities(value: String): String = buildString {
    value.codePoints().forEach { codePoint ->
        appendCodePoint(codePoint)
        append('\u200B')
    }
}

@Composable
internal fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
internal fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = remember(value) { insertLineBreakOpportunities(value) },
            style = MaterialTheme.typography.bodyMedium.merge(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun InfoCardLongValuePreview() {
    MaterialTheme {
        InfoCard(title = "ファイル情報") {
            InfoRow(
                label = "パス",
                value = "/storage/emulated/0/very/long/path/to/a/file/without/spaces/that/needs/wrapping/photo📸backup.jpg",
            )
            InfoRow(
                label = "エラー",
                value = "接続がタイムアウトしました: java.net.SocketTimeoutException: connect timed out after 30000ms",
            )
        }
    }
}
