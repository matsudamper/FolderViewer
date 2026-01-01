package net.matsudamper.folderviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import net.matsudamper.folderviewer.coil.CoilImageLoaderFactory
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
import net.matsudamper.folderviewer.viewmodel.storage.SmbAddViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FolderViewerTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent(
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
            SettingsScreen(
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

            LaunchedEffect(viewModel.event) {
                viewModel.event.collect { event ->
                    when (event) {
                        SmbAddViewModel.Event.SaveSuccess -> {
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
            val fileRepository by viewModel.fileRepository.collectAsState()

            LaunchedEffect(viewModel.event) {
                viewModel.event.collect { event ->
                    when (event) {
                        is FileBrowserViewModel.Event.PopBackStack -> {
                            navController.popBackStack()
                        }

                        is FileBrowserViewModel.Event.NavigateToFileBrowser -> {
                            navController.navigate(
                                FileBrowser(
                                    storageId = event.storageId,
                                    path = event.path,
                                ),
                            )
                        }

                        is FileBrowserViewModel.Event.NavigateToImageViewer -> {
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

            val context = LocalContext.current
            val imageLoader = remember(fileRepository) {
                if (fileRepository != null) {
                    CoilImageLoaderFactory.create(context, fileRepository)
                } else {
                    null
                }
            }

            if (imageLoader != null) {
                FileBrowserScreen(
                    uiState = uiState,
                    imageLoader = imageLoader,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        composable<ImageViewer> {
            val viewModel: ImageViewerViewModel = hiltViewModel()
            val fileRepository by viewModel.fileRepository.collectAsState()

            val context = LocalContext.current
            val imageLoader = remember(fileRepository) {
                if (fileRepository != null) {
                    CoilImageLoaderFactory.create(context, fileRepository)
                } else {
                    null
                }
            }

            if (imageLoader != null) {
                ImageViewerScreen(
                    imageLoader = imageLoader,
                    path = viewModel.path,
                    onBack = { navController.popBackStack() },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
