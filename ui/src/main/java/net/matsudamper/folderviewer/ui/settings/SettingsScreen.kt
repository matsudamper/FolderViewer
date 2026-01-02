package net.matsudamper.folderviewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.browser.FileBrowserTopBar

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    onClearDiskCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FileBrowserTopBar(
                title = stringResource(R.string.settings),
                onBack = onBack,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(text = stringResource(R.string.cache))
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onClearDiskCache,
                ) {
                    Text(text = stringResource(R.string.clear_disk_cache))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        snackbarHostState = remember { SnackbarHostState() },
        onClearDiskCache = {},
        onBack = {},
    )
}
