package net.matsudamper.folderviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import javax.inject.Inject
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.navigation.Home
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.navigation.Settings
import net.matsudamper.folderviewer.navigation.SmbAdd
import net.matsudamper.folderviewer.navigation.StorageTypeSelection
import net.matsudamper.folderviewer.ui.browser.FileBrowserScreen
import net.matsudamper.folderviewer.ui.browser.ImageViewerScreen
import net.matsudamper.folderviewer.ui.home.HomeScreen
import net.matsudamper.folderviewer.ui.settings.SettingsScreen
import net.matsudamper.folderviewer.ui.storage.SmbAddScreen
import net.matsudamper.folderviewer.ui.storage.StorageTypeSelectionScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.viewmodel.browser.FileBrowserViewModel
import net.matsudamper.folderviewer.viewmodel.browser.ImageViewerViewModel
import net.matsudamper.folderviewer.viewmodel.home.HomeViewModel
import net.matsudamper.folderviewer.viewmodel.settings.SettingsViewModel
import net.matsudamper.folderviewer.viewmodel.storage.SmbAddViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FolderViewerTheme {
                AppContent(imageLoader = imageLoader)
            }
        }
    }
}

@Composable
private fun AppContent(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            val viewModel: HomeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            HomeScreen(
                uiState = uiState,
                onNavigateToSettings = {
                    navController.navigate(Settings)
                },
                onAddStorageClick = {
                    navController.navigate(StorageTypeSelection)
                },
                onStorageClick = { storage ->
                    navController.navigate(FileBrowser(storageId = storage.id))
                },
                onEditStorageClick = { storage ->
                    navController.navigate(SmbAdd(storageId = storage.id))
                },
            )
        }
        composable<Settings> {
            val viewModel: SettingsViewModel = hiltViewModel()
            val snackbarHostState = remember { SnackbarHostState() }

            SettingsScreen(
                snackbarHostState = snackbarHostState,
                uiEvent = viewModel.uiEvent,
                onClearDiskCache = viewModel::clearDiskCache,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable<StorageTypeSelection> {
            StorageTypeSelectionScreen(
                onSmbClick = {
                    navController.navigate(SmbAdd())
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable<SmbAdd> {
            val viewModel: SmbAddViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(viewModel.viewModelEventFlow) {
                viewModel.viewModelEventFlow.collect { event ->
                    when (event) {
                        SmbAddViewModel.ViewModelEvent.SaveSuccess -> {
                            navController.popBackStack(Home, inclusive = false)
                        }
                    }
                }
            }

            SmbAddScreen(
                uiState = uiState,
                onSave = viewModel::onSave,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable<FileBrowser> {
            val viewModel: FileBrowserViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(viewModel.viewModelEventFlow) {
                viewModel.viewModelEventFlow.collect { event ->
                    when (event) {
                        is FileBrowserViewModel.ViewModelEvent.PopBackStack -> {
                            navController.popBackStack()
                        }

                        is FileBrowserViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                            navController.navigate(
                                FileBrowser(
                                    storageId = event.storageId,
                                    path = event.path,
                                ),
                            )
                        }

                        is FileBrowserViewModel.ViewModelEvent.NavigateToImageViewer -> {
                            navController.navigate(
                                ImageViewer(
                                    id = event.storageId,
                                    path = event.path,
                                ),
                            )
                        }
                    }
                }
            }

            FileBrowserScreen(
                uiState = uiState,
                uiEvent = viewModel.uiEvent,
                imageLoader = imageLoader,
            )
        }
        composable<ImageViewer> {
            val viewModel: ImageViewerViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(viewModel.viewModelEventFlow) {
                viewModel.viewModelEventFlow.collect { event ->
                    when (event) {
                        is ImageViewerViewModel.ViewModelEvent.PopBackStack -> {
                            navController.popBackStack()
                        }
                    }
                }
            }

            ImageViewerScreen(
                uiState = uiState,
                imageLoader = imageLoader,
                onBack = {
                    uiState.callbacks.onBack()
                },
            )
        }
    }
}
