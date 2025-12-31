package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import net.matsudamper.folderviewer.repository.FileRepository

@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    fileRepository: FileRepository?,
    onErrorMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPath = uiState.currentPath
    val callbacks = uiState.callbacks

    BackHandler(enabled = currentPath.isNotEmpty()) {
        callbacks.onUpClick()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error
    LaunchedEffect(errorMessage) {
        errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(errorMessage)
        onErrorMessageShown()
    }

    val context = LocalContext.current
    val imageLoader = remember(fileRepository) {
        ImageLoader.Builder(context)
            .components {
                if (fileRepository != null) {
                    add(FileRepositoryImageFetcher.Factory(fileRepository))
                }
            }
            .build()
    }

    FileBrowserScreenContent(
        modifier = modifier,
        uiState = uiState,
        imageLoader = imageLoader,
        snackbarHostState = snackbarHostState,
    )
}
