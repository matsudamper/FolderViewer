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
internal fun SmbAddForm(
    uiState: SmbAddUiState,
    modifier: Modifier = Modifier,
) {
    var name by remember(uiState.name) { mutableStateOf(uiState.name) }
    var ip by remember(uiState.ip) { mutableStateOf(uiState.ip) }
    var username by remember(uiState.username) { mutableStateOf(uiState.username) }
    var password by remember(uiState.password) { mutableStateOf(uiState.password) }

    val ipFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

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
            keyboardActions = KeyboardActions(onNext = { ipFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("IP Address / Hostname") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { usernameFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth().focusRequester(ipFocusRequester),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
            modifier = Modifier.fillMaxWidth().focusRequester(usernameFocusRequester),
        )
        Spacer(modifier = Modifier.height(PaddingSmall))
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
        )
        Spacer(modifier = Modifier.height(SpacerLarge))
        Button(
            onClick = {
                uiState.callbacks.onSave(
                    SmbInput(
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
