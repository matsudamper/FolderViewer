package net.matsudamper.folderviewer.ui.storage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun SharePointAddForm(
    uiState: SharePointAddUiState,
    modifier: Modifier = Modifier,
) {
    var name by remember(uiState.name) { mutableStateOf(uiState.name) }
    var objectId by remember(uiState.objectId) { mutableStateOf(uiState.objectId) }
    var tenantId by remember(uiState.tenantId) { mutableStateOf(uiState.tenantId) }
    var clientId by remember(uiState.clientId) { mutableStateOf(uiState.clientId) }
    var clientSecret by remember(uiState.clientSecret) { mutableStateOf(uiState.clientSecret) }

    val objectIdFocusRequester = remember { FocusRequester() }
    val tenantIdFocusRequester = remember { FocusRequester() }
    val clientIdFocusRequester = remember { FocusRequester() }
    val clientSecretFocusRequester = remember { FocusRequester() }

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
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { objectIdFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        OutlinedTextField(
            value = objectId,
            onValueChange = { objectId = it },
            label = { Text("User Object ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { tenantIdFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth().focusRequester(objectIdFocusRequester),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        OutlinedTextField(
            value = tenantId,
            onValueChange = { tenantId = it },
            label = { Text("Tenant ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { clientIdFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth().focusRequester(tenantIdFocusRequester),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it },
            label = { Text("Client ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { clientSecretFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth().focusRequester(clientIdFocusRequester),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        PasswordTextField(
            value = clientSecret,
            onValueChange = { clientSecret = it },
            label = { Text("Client Secret") },
            singleLine = true,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth().focusRequester(clientSecretFocusRequester),
        )
        Spacer(modifier = Modifier.height(SpacerLarge))
        Button(
            onClick = {
                uiState.callbacks.onSave(
                    SharePointInput(
                        name = name,
                        objectId = objectId,
                        tenantId = tenantId,
                        clientId = clientId,
                        clientSecret = clientSecret,
                    ),
                )
            },
            enabled = name.isNotBlank() && objectId.isNotBlank() && tenantId.isNotBlank() &&
                clientId.isNotBlank() && clientSecret.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
    }
}

private val PaddingNormal = 16.dp
private val PaddingSmall = 8.dp
private val SpacerLarge = 24.dp
