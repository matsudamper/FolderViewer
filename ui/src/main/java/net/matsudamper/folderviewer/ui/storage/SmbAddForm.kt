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
import net.matsudamper.folderviewer.repository.StorageRepository

@Composable
internal fun SmbAddForm(
    uiState: SmbAddViewModel.UiState,
    onSave: (StorageRepository.SmbStorageInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(uiState.name) { mutableStateOf(uiState.name) }
    var ip by remember(uiState.ip) { mutableStateOf(uiState.ip) }
    var username by remember(uiState.username) { mutableStateOf(uiState.username) }
    var password by remember(uiState.password) { mutableStateOf(uiState.password) }

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
            value = ip,
            onValueChange = { ip = it },
            label = { Text("IP Address / Hostname") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SpacerLarge))
        Button(
            onClick = {
                onSave(
                    StorageRepository.SmbStorageInput(
                        name = name,
                        ip = ip,
                        username = username,
                        password = password,
                    ),
                )
            },
            enabled = name.isNotBlank() && ip.isNotBlank(), // 基本的なバリデーション
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
    }
}

private val PaddingNormal = 16.dp
private val PaddingSmall = 8.dp
private val SpacerLarge = 24.dp
