package net.matsudamper.folderviewer.ui.storage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SharePointAddForm(
    uiState: SharePointAddUiState,
    modifier: Modifier = Modifier,
) {
    var name by remember(uiState.name) { mutableStateOf(uiState.name) }
    var siteUrl by remember(uiState.siteUrl) { mutableStateOf(uiState.siteUrl) }
    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingNormal)
            .verticalScroll(rememberScrollState()),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        OutlinedTextField(
            value = siteUrl,
            onValueChange = { siteUrl = it },
            label = { Text("SharePoint Site URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        PasswordTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SpacerLarge))
        Button(
            onClick = {
                uiState.callbacks.onSave(
                    SharePointInput(
                        name = name,
                        siteUrl = siteUrl,
                        apiKey = apiKey,
                    ),
                )
            },
            enabled = name.isNotBlank() && siteUrl.isNotBlank() && apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
    }
}

private val PaddingNormal = 16.dp
private val PaddingSmall = 8.dp
private val SpacerLarge = 24.dp
