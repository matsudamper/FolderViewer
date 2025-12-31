package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.matsudamper.folderviewer.repository.FileItem

@Composable
fun FileBrowserScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FileBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val currentPath = uiState.currentPath
    BackHandler(enabled = currentPath.isNotEmpty()) {
        viewModel.onBackClick()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error
    LaunchedEffect(errorMessage) {
        errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(errorMessage)
        viewModel.errorMessageShown()
    }

    val callbacks = remember(onBack, viewModel) {
        object : FileBrowserUiState.Callbacks {
            override val onBack: () -> Unit = onBack
            override val onFileClick: (FileItem) -> Unit = viewModel::onFileClick
            override val onUpClick: () -> Unit = viewModel::onBackClick
            override val onRefresh: () -> Unit = viewModel::onRefresh
        }
    }

    FileBrowserScreenContent(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        callbacks = callbacks,
    )
}
