package net.matsudamper.folderviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
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
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.navigation.FolderBrowser
import net.matsudamper.folderviewer.navigation.Home
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.navigation.Navigator
import net.matsudamper.folderviewer.navigation.PermissionRequest
import net.matsudamper.folderviewer.navigation.Settings
import net.matsudamper.folderviewer.navigation.SharePointAdd
import net.matsudamper.folderviewer.navigation.SmbAdd
import net.matsudamper.folderviewer.navigation.StorageTypeSelection
import net.matsudamper.folderviewer.navigation.rememberNavigationState
import net.matsudamper.folderviewer.navigation.toEntries
import net.matsudamper.folderviewer.repository.PermissionUtil
import net.matsudamper.folderviewer.ui.browser.FileBrowserScreen
import net.matsudamper.folderviewer.ui.browser.ImageViewerScreen
import net.matsudamper.folderviewer.ui.folder.FolderBrowserScreen
import net.matsudamper.folderviewer.ui.home.HomeScreen
import net.matsudamper.folderviewer.ui.permission.PermissionRequestScreen
import net.matsudamper.folderviewer.ui.settings.SettingsScreen
import net.matsudamper.folderviewer.ui.storage.SharePointAddScreen
import net.matsudamper.folderviewer.ui.storage.SmbAddScreen
import net.matsudamper.folderviewer.ui.storage.StorageTypeSelectionScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.viewmodel.browser.FileBrowserViewModel
import net.matsudamper.folderviewer.viewmodel.browser.ImageViewerViewModel
import net.matsudamper.folderviewer.viewmodel.folder.FolderBrowserViewModel
import net.matsudamper.folderviewer.viewmodel.home.HomeViewModel
import net.matsudamper.folderviewer.viewmodel.permission.PermissionRequestViewModel
import net.matsudamper.folderviewer.viewmodel.settings.SettingsViewModel
import net.matsudamper.folderviewer.viewmodel.storage.SharePointAddViewModel
import net.matsudamper.folderviewer.viewmodel.storage.SmbAddViewModel
import net.matsudamper.folderviewer.viewmodel.storage.StorageTypeSelectionViewModel

private fun collectFiles(
    folder: DocumentFile,
    relativePath: String,
    files: MutableList<Pair<android.net.Uri, String>>,
) {
    folder.listFiles().forEach { file ->
        if (file.isDirectory) {
            val newRelativePath = if (relativePath.isEmpty()) file.name.orEmpty() else "$relativePath/${file.name}"
            collectFiles(file, newRelativePath, files)
        } else {
            val filePath = if (relativePath.isEmpty()) file.name.orEmpty() else "$relativePath/${file.name}"
            file.uri?.let { uri ->
                files.add(uri to filePath)
            }
        }
    }
}

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
        sharePointAddEntry(navigator)
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

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    HomeViewModel.ViewModelEvent.NavigateToSettings -> {
                        navigator.navigate(Settings)
                    }

                    HomeViewModel.ViewModelEvent.NavigateToStorageTypeSelection -> {
                        navigator.navigate(StorageTypeSelection)
                    }

                    is HomeViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                        navigator.navigate(FileBrowser(storageId = event.storageId, displayPath = null, fileId = FileObjectId.Root))
                    }

                    is HomeViewModel.ViewModelEvent.NavigateToSmbAdd -> {
                        navigator.navigate(SmbAdd(storageId = event.storageId))
                    }

                    is HomeViewModel.ViewModelEvent.NavigateToSharePointAdd -> {
                        navigator.navigate(SharePointAdd(storageId = event.storageId))
                    }
                }
            }
        }

        HomeScreen(
            uiState = uiState,
        )
    }
}

private fun EntryProviderScope<NavKey>.settingsEntry(navigator: Navigator) {
    entry<Settings> {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    SettingsViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }
                }
            }
        }

        SettingsScreen(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            uiEvent = viewModel.uiEventFlow,
        )
    }
}

private fun EntryProviderScope<NavKey>.storageTypeSelectionEntry(
    navigator: Navigator,
) {
    entry<StorageTypeSelection> {
        val viewModel: StorageTypeSelectionViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
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

                    StorageTypeSelectionViewModel.ViewModelEvent.NavigateToSmbAdd -> {
                        navigator.navigate(SmbAdd())
                    }

                    StorageTypeSelectionViewModel.ViewModelEvent.NavigateToSharePointAdd -> {
                        navigator.navigate(SharePointAdd())
                    }

                    StorageTypeSelectionViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
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
            uiState = uiState,
            snackbarHostState = snackbarHostState,
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

                    SmbAddViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }
                }
            }
        }

        SmbAddScreen(
            uiState = uiState,
        )
    }
}

private fun EntryProviderScope<NavKey>.sharePointAddEntry(navigator: Navigator) {
    entry<SharePointAdd> { key ->
        val viewModel: SharePointAddViewModel = hiltViewModel<
            SharePointAddViewModel,
            SharePointAddViewModel.Companion.Factory,
            >(
            creationCallback = { factory: SharePointAddViewModel.Companion.Factory ->
                factory.create(arguments = key)
            },
        )
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    SharePointAddViewModel.ViewModelEvent.SaveSuccess -> {
                        navigator.popBackStack(Home, inclusive = false)
                    }

                    SharePointAddViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }
                }
            }
        }

        SharePointAddScreen(
            uiState = uiState,
        )
    }
}

@Composable
private fun FileBrowserEventHandler(
    viewModel: FileBrowserViewModel,
    navigator: Navigator,
    filePickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    folderPickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<android.net.Uri?, android.net.Uri?>,
) {
    LaunchedEffect(viewModel.viewModelEventFlow) {
        viewModel.viewModelEventFlow.collect { event ->
            when (event) {
                is FileBrowserViewModel.ViewModelEvent.PopBackStack -> navigator.goBack()

                is FileBrowserViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                    navigator.navigate(FileBrowser(storageId = event.storageId, displayPath = event.displayPath, fileId = FileObjectId.Item(event.id)))
                }

                is FileBrowserViewModel.ViewModelEvent.NavigateToImageViewer -> {
                    navigator.navigate(ImageViewer(id = event.storageId, path = event.path, allPaths = event.allPaths))
                }

                is FileBrowserViewModel.ViewModelEvent.NavigateToFolderBrowser -> {
                    navigator.navigate(
                        FolderBrowser(
                            storageId = event.storageId,
                            displayPath = event.displayPath,
                            fileId = event.id,
                        ),
                    )
                }

                is FileBrowserViewModel.ViewModelEvent.LaunchFilePicker -> filePickerLauncher.launch("*/*")

                is FileBrowserViewModel.ViewModelEvent.LaunchFolderPicker -> folderPickerLauncher.launch(null)
            }
        }
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
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let { selectedUri ->
                val fileName = DocumentFile.fromSingleUri(context, selectedUri)?.name ?: "uploaded_file"
                scope.launch {
                    viewModel.handleFileUpload(selectedUri, fileName)
                }
            }
        }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let { treeUri ->
                val documentFile = DocumentFile.fromTreeUri(context, treeUri)
                documentFile?.let { folder ->
                    val files = mutableListOf<Pair<android.net.Uri, String>>()
                    collectFiles(folder, "", files)
                    scope.launch {
                        viewModel.handleFolderUpload(files)
                    }
                }
            }
        }

        FileBrowserEventHandler(viewModel, navigator, filePickerLauncher, folderPickerLauncher)

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
                                displayPath = event.path,
                                fileId = FileObjectId.Item(event.path),
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
                                fileId = FileObjectId.Item(event.path),
                                displayPath = event.displayPath,
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
