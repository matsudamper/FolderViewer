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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.navigation.FolderBrowser
import net.matsudamper.folderviewer.navigation.Home
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.navigation.Navigator
import net.matsudamper.folderviewer.navigation.PermissionRequest
import net.matsudamper.folderviewer.navigation.Settings
import net.matsudamper.folderviewer.navigation.SmbAdd
import net.matsudamper.folderviewer.navigation.StorageTypeSelection
import net.matsudamper.folderviewer.navigation.rememberNavigationState
import net.matsudamper.folderviewer.navigation.toEntries
import net.matsudamper.folderviewer.repository.PermissionUtil
import net.matsudamper.folderviewer.ui.browser.FileBrowserScreen
import net.matsudamper.folderviewer.ui.browser.ImageViewerScreen
import net.matsudamper.folderviewer.ui.folder.FolderBrowserScreen
import net.matsudamper.folderviewer.ui.home.HomeScreen
import net.matsudamper.folderviewer.ui.home.UiStorageConfiguration
import net.matsudamper.folderviewer.ui.permission.PermissionRequestScreen
import net.matsudamper.folderviewer.ui.settings.SettingsScreen
import net.matsudamper.folderviewer.ui.storage.SmbAddScreen
import net.matsudamper.folderviewer.ui.storage.StorageTypeSelectionScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.viewmodel.browser.FileBrowserViewModel
import net.matsudamper.folderviewer.viewmodel.browser.ImageViewerViewModel
import net.matsudamper.folderviewer.viewmodel.folder.FolderBrowserViewModel
import net.matsudamper.folderviewer.viewmodel.home.HomeViewModel
import net.matsudamper.folderviewer.viewmodel.permission.PermissionRequestViewModel
import net.matsudamper.folderviewer.viewmodel.settings.SettingsViewModel
import net.matsudamper.folderviewer.viewmodel.storage.SmbAddViewModel
import net.matsudamper.folderviewer.viewmodel.storage.StorageTypeSelectionViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(imageLoader)

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
    val navigationState = rememberNavigationState(
        startRoute = Home,
        topLevelRoutes = setOf(Home),
    )
    val navigator = remember { Navigator(navigationState) }

    val entryProvider = entryProvider {
        homeEntry(navigator)
        settingsEntry(navigator)
        storageTypeSelectionEntry(navigator)
        permissionRequestEntry(navigator)
        smbAddEntry(navigator)
        fileBrowserEntry(navigator)
        folderBrowserEntry(navigator)
        imageViewerEntry(navigator)
    }

    NavDisplay(
        modifier = modifier,
        entries = navigationState.toEntries(entryProvider),
        onBack = { navigator.goBack() },
    )
}

private fun EntryProviderScope<NavKey>.homeEntry(navigator: Navigator) {
    entry<Home> {
        val viewModel: HomeViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        HomeScreen(
            uiState = uiState,
            onNavigateToSettings = {
                navigator.navigate(Settings)
            },
            onAddStorageClick = {
                navigator.navigate(StorageTypeSelection)
            },
            onStorageClick = { storage ->
                navigator.navigate(FileBrowser(storageId = storage.id, path = null))
            },
            onEditStorageClick = { storage ->
                when (storage) {
                    is UiStorageConfiguration.Smb -> {
                        navigator.navigate(SmbAdd(storageId = storage.id))
                    }

                    is UiStorageConfiguration.Local -> {
                    }
                }
            },
            onDeleteStorageClick = viewModel::onDeleteStorage,
        )
    }
}

private fun EntryProviderScope<NavKey>.settingsEntry(navigator: Navigator) {
    entry<Settings> {
        val viewModel: SettingsViewModel = hiltViewModel()
        val snackbarHostState = remember { SnackbarHostState() }

        SettingsScreen(
            snackbarHostState = snackbarHostState,
            uiEvent = viewModel.uiEventFlow,
            onClearDiskCache = viewModel::clearDiskCache,
            onBack = {
                navigator.goBack()
            },
        )
    }
}

private fun EntryProviderScope<NavKey>.storageTypeSelectionEntry(
    navigator: Navigator,
) {
    entry<StorageTypeSelection> {
        val viewModel: StorageTypeSelectionViewModel = hiltViewModel()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    StorageTypeSelectionViewModel.ViewModelEvent.NavigateToHome -> {
                        navigator.popBackStack(Home, inclusive = false)
                    }

                    StorageTypeSelectionViewModel.ViewModelEvent.NavigateToPermissionRequest -> {
                        navigator.navigate(PermissionRequest)
                    }

                    StorageTypeSelectionViewModel.ViewModelEvent.ShowAlreadyAddedMessage -> {
                        scope.launch {
                            snackbarHostState.showSnackbar("追加済です")
                        }
                    }
                }
            }
        }

        StorageTypeSelectionScreen(
            snackbarHostState = snackbarHostState,
            onSmbClick = {
                navigator.navigate(SmbAdd())
            },
            onLocalClick = viewModel::onLocalClick,
            onBack = {
                navigator.goBack()
            },
        )
    }
}

private fun EntryProviderScope<NavKey>.permissionRequestEntry(
    navigator: Navigator,
) {
    entry<PermissionRequest> {
        val viewModel: PermissionRequestViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    PermissionRequestViewModel.ViewModelEvent.OpenSettings -> {
                        val intent = PermissionUtil.createManageStorageIntent(context)
                        context.startActivity(intent)
                    }

                    PermissionRequestViewModel.ViewModelEvent.PermissionGranted -> {
                        navigator.popBackStack(Home, inclusive = false)
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.checkPermission()
        }

        PermissionRequestScreen(
            uiState = uiState,
            onGrantPermission = viewModel::onGrantPermission,
        )
    }
}

private fun EntryProviderScope<NavKey>.smbAddEntry(navigator: Navigator) {
    entry<SmbAdd> { key ->
        val viewModel: SmbAddViewModel = hiltViewModel<SmbAddViewModel, SmbAddViewModel.Companion.Factory>(
            creationCallback = { factory: SmbAddViewModel.Companion.Factory ->
                factory.create(arguments = key)
            },
        )
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    SmbAddViewModel.ViewModelEvent.SaveSuccess -> {
                        navigator.popBackStack(Home, inclusive = false)
                    }
                }
            }
        }

        SmbAddScreen(
            uiState = uiState,
            onSave = viewModel::onSave,
            onBack = {
                navigator.goBack()
            },
        )
    }
}

private fun EntryProviderScope<NavKey>.fileBrowserEntry(navigator: Navigator) {
    entry<FileBrowser> { key ->
        val viewModel: FileBrowserViewModel = hiltViewModel<
            FileBrowserViewModel,
            FileBrowserViewModel.Companion.Factory,
            >(
            creationCallback = { factory: FileBrowserViewModel.Companion.Factory ->
                factory.create(arguments = key)
            },
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle(initialValue = null)
        val uiStateValue = uiState.value ?: return@entry

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    is FileBrowserViewModel.ViewModelEvent.PopBackStack -> {
                        navigator.goBack()
                    }

                    is FileBrowserViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                        navigator.navigate(
                            FileBrowser(
                                storageId = event.storageId,
                                path = event.path,
                            ),
                        )
                    }

                    is FileBrowserViewModel.ViewModelEvent.NavigateToImageViewer -> {
                        navigator.navigate(
                            ImageViewer(
                                id = event.storageId,
                                path = event.path,
                                allPaths = event.allPaths,
                            ),
                        )
                    }

                    is FileBrowserViewModel.ViewModelEvent.NavigateToFolderBrowser -> {
                        navigator.navigate(
                            FolderBrowser(
                                storageId = event.storageId,
                                path = event.path,
                            ),
                        )
                    }
                }
            }
        }

        FileBrowserScreen(
            uiState = uiStateValue,
            uiEvent = viewModel.uiEvent,
        )
    }
}

private fun EntryProviderScope<NavKey>.folderBrowserEntry(navigator: Navigator) {
    entry<FolderBrowser> { key ->
        val viewModel: FolderBrowserViewModel = hiltViewModel<
            FolderBrowserViewModel,
            FolderBrowserViewModel.Companion.Factory,
            >(
            creationCallback = { factory: FolderBrowserViewModel.Companion.Factory ->
                factory.create(arguments = key)
            },
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle(initialValue = null)
        val uiStateValue = uiState.value ?: return@entry

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    is FolderBrowserViewModel.ViewModelEvent.PopBackStack -> {
                        navigator.goBack()
                    }

                    is FolderBrowserViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                        navigator.navigate(
                            FileBrowser(
                                storageId = event.storageId,
                                path = event.path,
                            ),
                        )
                    }

                    is FolderBrowserViewModel.ViewModelEvent.NavigateToImageViewer -> {
                        navigator.navigate(
                            ImageViewer(
                                id = event.storageId,
                                path = event.path,
                                allPaths = event.allPaths,
                            ),
                        )
                    }

                    is FolderBrowserViewModel.ViewModelEvent.NavigateToFolderBrowser -> {
                        navigator.navigate(
                            FolderBrowser(
                                storageId = event.storageId,
                                path = event.path,
                            ),
                        )
                    }
                }
            }
        }

        FolderBrowserScreen(
            uiState = uiStateValue,
            uiEvent = viewModel.uiEvent,
        )
    }
}

private fun EntryProviderScope<NavKey>.imageViewerEntry(navigator: Navigator) {
    entry<ImageViewer> { key ->
        val viewModel: ImageViewerViewModel = hiltViewModel<
            ImageViewerViewModel,
            ImageViewerViewModel.Companion.Factory,
            >(
            creationCallback = { factory: ImageViewerViewModel.Companion.Factory ->
                factory.create(arguments = key)
            },
        )
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    is ImageViewerViewModel.ViewModelEvent.PopBackStack -> {
                        navigator.goBack()
                    }
                }
            }
        }

        ImageViewerScreen(
            uiState = uiState,
        )
    }
}
