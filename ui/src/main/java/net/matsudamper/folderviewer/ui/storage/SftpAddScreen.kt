package net.matsudamper.folderviewer.ui.storage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun SftpAddScreen(
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SftpAddViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel.event, onSaveSuccess) {
        viewModel.event.collect { event ->
            when (event) {
                SftpAddViewModel.Event.SaveSuccess -> {
                    onSaveSuccess()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SftpAddTopBar(
                isEditMode = uiState.isEditMode,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SftpAddForm(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                onSave = viewModel::onSave,
            )
        }
    }
}
