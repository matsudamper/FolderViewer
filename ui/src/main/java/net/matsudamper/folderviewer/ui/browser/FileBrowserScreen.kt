package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.ui.R

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

    FileBrowserScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onFileClick = viewModel::onFileClick,
        onUpClick = viewModel::onBackClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileBrowserScreenContent(
    uiState: FileBrowserUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.currentPath.ifEmpty { stringResource(R.string.root) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (uiState.currentPath.isEmpty()) onBack else onUpClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.files.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_files),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.files) { file ->
                        FileListItem(
                            file = file,
                            onClick = { onFileClick(file) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(
    file: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(
                    id = if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file,
                ),
                contentDescription = null,
            )
        },
        headlineContent = { Text(file.name) },
        supportingContent = {
            if (!file.isDirectory) {
                Text("${file.size} bytes")
            }
        },
    )
}
