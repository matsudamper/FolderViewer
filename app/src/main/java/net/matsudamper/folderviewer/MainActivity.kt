package net.matsudamper.folderviewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import java.io.File
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.navigation.DeleteDetail
import net.matsudamper.folderviewer.navigation.ExtractDetail
import net.matsudamper.folderviewer.navigation.FileBrowser
import net.matsudamper.folderviewer.navigation.FolderBrowser
import net.matsudamper.folderviewer.navigation.Home
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.navigation.Navigator
import net.matsudamper.folderviewer.navigation.PasteDetail
import net.matsudamper.folderviewer.navigation.PermissionRequest
import net.matsudamper.folderviewer.navigation.Settings
import net.matsudamper.folderviewer.navigation.SharePointAdd
import net.matsudamper.folderviewer.navigation.SmbAdd
import net.matsudamper.folderviewer.navigation.StorageTypeSelection
import net.matsudamper.folderviewer.navigation.UploadDetail
import net.matsudamper.folderviewer.navigation.UploadProgress
import net.matsudamper.folderviewer.navigation.rememberNavigationState
import net.matsudamper.folderviewer.navigation.toEntries
import net.matsudamper.folderviewer.repository.ExtractJobRepository
import net.matsudamper.folderviewer.repository.PermissionUtil
import net.matsudamper.folderviewer.repository.ViewSourceUri
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
import net.matsudamper.folderviewer.ui.upload.DeleteDetailScreen
import net.matsudamper.folderviewer.ui.upload.ExtractDetailScreen
import net.matsudamper.folderviewer.ui.upload.PasteDetailScreen
import net.matsudamper.folderviewer.ui.upload.UploadDetailScreen
import net.matsudamper.folderviewer.ui.upload.UploadProgressScreen
import net.matsudamper.folderviewer.ui.util.showDismissibleSnackbar
import net.matsudamper.folderviewer.viewmodel.browser.ExtractJobCompletionWatcher
import net.matsudamper.folderviewer.viewmodel.browser.FileBrowserViewModel
import net.matsudamper.folderviewer.viewmodel.browser.ImageViewerViewModel
import net.matsudamper.folderviewer.viewmodel.folder.FolderBrowserViewModel
import net.matsudamper.folderviewer.viewmodel.home.HomeViewModel
import net.matsudamper.folderviewer.viewmodel.permission.PermissionRequestViewModel
import net.matsudamper.folderviewer.viewmodel.settings.SettingsViewModel
import net.matsudamper.folderviewer.viewmodel.storage.SharePointAddViewModel
import net.matsudamper.folderviewer.viewmodel.storage.SmbAddViewModel
import net.matsudamper.folderviewer.viewmodel.storage.StorageTypeSelectionViewModel
import net.matsudamper.folderviewer.viewmodel.upload.DeleteDetailViewModel
import net.matsudamper.folderviewer.viewmodel.upload.ExtractDetailViewModel
import net.matsudamper.folderviewer.viewmodel.upload.PasteDetailViewModel
import net.matsudamper.folderviewer.viewmodel.upload.UploadDetailViewModel
import net.matsudamper.folderviewer.viewmodel.upload.UploadProgressViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var extractJobCompletionWatcher: ExtractJobCompletionWatcher

    @Inject
    lateinit var extractJobRepository: ExtractJobRepository

    private val navigateToUploadProgressRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateUploadProgressNavigation(intent)
        enableEdgeToEdge()
        Coil.setImageLoader(imageLoader)

        setContent {
            FolderViewerTheme {
                AppContent(
                    extractJobCompletionWatcher = extractJobCompletionWatcher,
                    extractJobRepository = extractJobRepository,
                    navigateToUploadProgressOnStart = navigateToUploadProgressRequest.value,
                    onUploadProgressNavigationHandled = { navigateToUploadProgressRequest.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateUploadProgressNavigation(intent)
    }

    private fun updateUploadProgressNavigation(intent: Intent?) {
        navigateToUploadProgressRequest.value = consumeUploadProgressNavigationIntent(intent)
    }

    private fun consumeUploadProgressNavigationIntent(intent: Intent?): Boolean {
        if (intent == null) {
            return false
        }
        val shouldNavigate = intent.getBooleanExtra(EXTRA_NAVIGATE_TO_UPLOAD_PROGRESS, false)
        if (shouldNavigate) {
            intent.removeExtra(EXTRA_NAVIGATE_TO_UPLOAD_PROGRESS)
        }
        return shouldNavigate
    }

    companion object {
        const val EXTRA_NAVIGATE_TO_UPLOAD_PROGRESS = "extra_navigate_to_upload_progress"
    }
}

@Composable
private fun AppContent(
    extractJobCompletionWatcher: ExtractJobCompletionWatcher,
    extractJobRepository: ExtractJobRepository,
    navigateToUploadProgressOnStart: Boolean,
    onUploadProgressNavigationHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { 2 }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }
    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(backDispatcherOwner, lifecycleOwner) {
        if (backDispatcherOwner == null) return@DisposableEffect onDispose { }
        val dispatcher = backDispatcherOwner.onBackPressedDispatcher
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                isPredictiveBackInProgress = true
            }

            override fun handleOnBackCancelled() {
                isPredictiveBackInProgress = false
            }

            override fun handleOnBackPressed() {
                isPredictiveBackInProgress = false
                isEnabled = false
                dispatcher.onBackPressed()
                isEnabled = true
            }
        }
        dispatcher.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val holder = rememberSaveableStateHolder()
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .edgeSwipeGuard(),
            state = pagerState,
            userScrollEnabled = !isPredictiveBackInProgress,
        ) { pageIndex ->
            holder.SaveableStateProvider("Root_$pageIndex") {
                val pageViewModelStoreOwner = rememberPageViewModelStoreOwner(pageIndex = pageIndex)
                val navigationState = rememberNavigationState(
                    startRoute = Home,
                    topLevelRoutes = setOf(Home),
                )
                val navigator = remember(navigationState) { Navigator(navigationState) }
                val entryProvider = remember(navigator) { entryProvider(navigator) }

                GlobalNavigationEffect(
                    pageIndex = pageIndex,
                    pagerState = pagerState,
                    navigator = navigator,
                    extractJobCompletionWatcher = extractJobCompletionWatcher,
                    extractJobRepository = extractJobRepository,
                    navigateToUploadProgressOnStart = navigateToUploadProgressOnStart,
                    onUploadProgressNavigationHandled = onUploadProgressNavigationHandled,
                )

                NavDisplay(
                    modifier = Modifier.fillMaxSize(),
                    entries = navigationState.toEntries(
                        entryProvider = entryProvider,
                        viewModelStoreOwner = pageViewModelStoreOwner,
                    ),
                    onBack = { navigator.goBack() },
                )
            }
        }

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
            visible = true
            delay(1.seconds)
            visible = false
        }
        AnimatedVisibility(
            visible = visible,
            enter = EnterTransition.None,
            exit = fadeOut(tween(durationMillis = 500)),
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(0.4f)),
            ) {
                IndicatorItem(isActive = pagerState.currentPage == 0)
                IndicatorItem(isActive = pagerState.currentPage == 1)
            }
        }
    }
}

@Composable
private fun GlobalNavigationEffect(
    pageIndex: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    navigator: Navigator,
    extractJobCompletionWatcher: ExtractJobCompletionWatcher,
    extractJobRepository: ExtractJobRepository,
    navigateToUploadProgressOnStart: Boolean,
    onUploadProgressNavigationHandled: () -> Unit,
) {
    LaunchedEffect(navigateToUploadProgressOnStart, pagerState.currentPage, pageIndex) {
        if (!navigateToUploadProgressOnStart || pagerState.currentPage != pageIndex) {
            return@LaunchedEffect
        }
        navigator.navigate(UploadProgress)
        onUploadProgressNavigationHandled()
    }

    LaunchedEffect(pagerState.currentPage, pageIndex, extractJobCompletionWatcher) {
        if (pagerState.currentPage != pageIndex) {
            return@LaunchedEffect
        }
        extractJobCompletionWatcher.pendingNavigation.collect { navigation ->
            navigator.navigate(
                FileBrowser(
                    displayPath = navigation.displayPath,
                    fileId = navigation.fileId,
                ),
            )
            extractJobRepository.markOpenOnCompleteHandled(navigation.jobId)
        }
    }
}

@Composable
private fun Modifier.edgeSwipeGuard(): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val leftEdgePx = WindowInsets.systemGestures.getLeft(density, layoutDirection)
    val rightEdgePx = WindowInsets.systemGestures.getRight(density, layoutDirection)

    if (leftEdgePx == 0 && rightEdgePx == 0) return this

    return pointerInput(leftEdgePx, rightEdgePx) {
        awaitEachGesture {
            val downEvent = awaitPointerEvent(PointerEventPass.Initial)
            val down = downEvent.changes.firstOrNull { it.pressed }
                ?: return@awaitEachGesture

            val isEdge = down.position.x < leftEdgePx ||
                down.position.x > size.width - rightEdgePx
            if (!isEdge) return@awaitEachGesture

            var totalDragX = 0f
            var exceededSlop = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break

                if (!exceededSlop) {
                    event.changes.forEach { change ->
                        totalDragX += abs(change.position.x - change.previousPosition.x)
                    }
                    exceededSlop = totalDragX > viewConfiguration.touchSlop
                }
                if (exceededSlop) {
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

@Composable
private fun IndicatorItem(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val size by animateDpAsState(targetValue = if (isActive) 12.dp else 6.dp)
    Box(
        modifier = modifier
            .size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun rememberPageViewModelStoreOwner(
    pageIndex: Int,
): ViewModelStoreOwner {
    val rootViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val hostViewModel = viewModel<PageViewModelStoreHostViewModel>(
        viewModelStoreOwner = rootViewModelStoreOwner,
    )
    val pageId = "page_$pageIndex"
    return remember(hostViewModel, pageId) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = hostViewModel.getViewModelStore(pageId)
        }
    }
}

@HiltViewModel
internal class PageViewModelStoreHostViewModel @Inject constructor() : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun getViewModelStore(pageId: String): ViewModelStore {
        return stores.getOrPut(pageId) { ViewModelStore() }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

internal fun entryProvider(navigator: Navigator): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        homeEntry(navigator)
        settingsEntry(navigator)
        storageTypeSelectionEntry(navigator)
        permissionRequestEntry(navigator)
        smbAddEntry(navigator)
        sharePointAddEntry(navigator)
        fileBrowserEntry(navigator)
        folderBrowserEntry(navigator)
        imageViewerEntry(navigator)
        uploadProgressEntry(navigator)
        uploadDetailEntry(navigator)
        pasteDetailEntry(navigator)
        deleteDetailEntry(navigator)
        extractDetailEntry(navigator)
    }
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
                        navigator.navigate(FileBrowser(displayPath = null, fileId = FileObjectId.Root(storageId = event.storageId)))
                    }

                    is HomeViewModel.ViewModelEvent.NavigateToSmbAdd -> {
                        navigator.navigate(SmbAdd(storageId = event.storageId))
                    }

                    is HomeViewModel.ViewModelEvent.NavigateToSharePointAdd -> {
                        navigator.navigate(SharePointAdd(storageId = event.storageId))
                    }

                    HomeViewModel.ViewModelEvent.NavigateToUploadProgress -> {
                        navigator.navigate(UploadProgress)
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
        val context = LocalContext.current

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    SettingsViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }

                    SettingsViewModel.ViewModelEvent.OpenGitHubReleases -> {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse(
                                context.getString(net.matsudamper.folderviewer.ui.R.string.github_releases_url),
                            )
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            snackbarHostState.showDismissibleSnackbar(
                                context.getString(
                                    net.matsudamper.folderviewer.ui.R.string.github_releases_open_error,
                                ),
                            )
                        }
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
                            snackbarHostState.showDismissibleSnackbar("追加済です")
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
        val viewModel: SharePointAddViewModel = hiltViewModel<SharePointAddViewModel, SharePointAddViewModel.Companion.Factory>(
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
    callbacks: net.matsudamper.folderviewer.ui.browser.FileBrowserUiState.Callbacks,
    filePickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    folderPickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<android.net.Uri?, android.net.Uri?>,
    pasteNotificationPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    deleteNotificationPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    extractNotificationPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel.viewModelEventFlow) {
        viewModel.viewModelEventFlow.collect { event ->
            when (event) {
                is FileBrowserViewModel.ViewModelEvent.PopBackStack -> navigator.goBack()

                is FileBrowserViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                    navigator.navigate(FileBrowser(displayPath = event.displayPath, fileId = event.id))
                }

                is FileBrowserViewModel.ViewModelEvent.NavigateToImageViewer -> {
                    navigator.navigate(ImageViewer(fileId = event.id, allPaths = event.allPaths))
                }

                is FileBrowserViewModel.ViewModelEvent.NavigateToFolderBrowser -> {
                    navigator.navigate(
                        FolderBrowser(
                            displayPath = event.displayPath,
                            fileId = event.id,
                        ),
                    )
                }

                is FileBrowserViewModel.ViewModelEvent.LaunchFilePicker -> filePickerLauncher.launch("*/*")

                is FileBrowserViewModel.ViewModelEvent.LaunchFolderPicker -> folderPickerLauncher.launch(null)

                is FileBrowserViewModel.ViewModelEvent.RequestNotificationPermissionForPaste -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        pasteNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        callbacks.onPastePermissionResult()
                    }
                }

                is FileBrowserViewModel.ViewModelEvent.RequestNotificationPermissionForDelete -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        deleteNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        callbacks.onDeletePermissionResult()
                    }
                }

                is FileBrowserViewModel.ViewModelEvent.RequestNotificationPermissionForExtract -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        extractNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        callbacks.onExtractPermissionResult()
                    }
                }

                is FileBrowserViewModel.ViewModelEvent.OpenFolderWithExternalApp -> {
                    val relativePath = File(event.path)
                        .relativeToOrNull(android.os.Environment.getExternalStorageDirectory())
                        ?.path
                        .orEmpty()
                    val uri = android.provider.DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:$relativePath",
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                    }
                    runCatching {
                        context.startActivity(intent)
                    }
                }

                is FileBrowserViewModel.ViewModelEvent.ShareFiles -> {
                    val uris = ArrayList<android.net.Uri>(event.items.size)
                    event.items.forEach { item ->
                        val uri = when (val externalUri = item.viewSourceUri) {
                            is ViewSourceUri.LocalFile -> FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                File(externalUri.path),
                            )

                            is ViewSourceUri.RemoteUrl -> StreamingContentProvider.buildUri(
                                fileId = item.fileId,
                                fileName = item.fileName,
                            )

                            is ViewSourceUri.StreamProvider -> StreamingContentProvider.buildUri(
                                fileId = externalUri.fileId,
                                fileName = item.fileName,
                            )
                        }
                        uris.add(uri)
                    }
                    val mimeTypes = event.items.map { it.mimeType ?: "*/*" }.distinct()
                    val commonMime = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
                    val sendIntent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_STREAM, uris.first())
                            type = commonMime
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            type = commonMime
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }
                }

                is FileBrowserViewModel.ViewModelEvent.OpenWithExternalPlayer -> {
                    openWithExternalApp(
                        context = context,
                        viewSourceUri = event.viewSourceUri,
                        fileName = event.fileName,
                        mimeType = event.mimeType,
                    )
                }
            }
        }
    }
}

private fun openWithExternalApp(
    context: android.content.Context,
    viewSourceUri: ViewSourceUri,
    fileName: String,
    mimeType: String?,
) {
    val uri = when (viewSourceUri) {
        is ViewSourceUri.LocalFile -> {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(viewSourceUri.path),
            )
        }

        is ViewSourceUri.RemoteUrl -> {
            viewSourceUri.url.toUri()
        }

        is ViewSourceUri.StreamProvider -> {
            StreamingContentProvider.buildUri(
                fileId = viewSourceUri.fileId,
                fileName = fileName,
            )
        }
    }
    val isApk = mimeType == "application/vnd.android.package-archive"
    if (isApk && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                },
            )
        }
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(intent)
    }
}

private fun requestNotificationPermissionIfNeeded(
    context: android.content.Context,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun EntryProviderScope<NavKey>.fileBrowserEntry(navigator: Navigator) {
    entry<FileBrowser> { key ->
        val viewModel: FileBrowserViewModel = hiltViewModel<FileBrowserViewModel, FileBrowserViewModel.Companion.Factory>(
            creationCallback = { factory: FileBrowserViewModel.Companion.Factory ->
                factory.create(arguments = key)
            },
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle(initialValue = null)
        val uiStateValue = uiState.value ?: return@entry
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
        }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            requestNotificationPermissionIfNeeded(context, notificationPermissionLauncher)
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            val fileName = if (documentFile == null) {
                "uploaded_file"
            } else {
                val name = documentFile.name
                if (name == null) "uploaded_file" else name
            }
            scope.launch {
                viewModel.handleFileUpload(uri, fileName)
            }
        }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            requestNotificationPermissionIfNeeded(context, notificationPermissionLauncher)
            scope.launch {
                viewModel.handleFolderUpload(uri)
            }
        }

        val pasteNotificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { _ ->
            uiStateValue.callbacks.onPastePermissionResult()
        }

        val deleteNotificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { _ ->
            uiStateValue.callbacks.onDeletePermissionResult()
        }

        val extractNotificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { _ ->
            uiStateValue.callbacks.onExtractPermissionResult()
        }

        FileBrowserEventHandler(
            viewModel = viewModel,
            navigator = navigator,
            callbacks = uiStateValue.callbacks,
            filePickerLauncher = filePickerLauncher,
            folderPickerLauncher = folderPickerLauncher,
            pasteNotificationPermissionLauncher = pasteNotificationPermissionLauncher,
            deleteNotificationPermissionLauncher = deleteNotificationPermissionLauncher,
            extractNotificationPermissionLauncher = extractNotificationPermissionLauncher,
        )

        FileBrowserScreen(
            uiState = uiStateValue,
            uiEvent = viewModel.uiEvent,
            onNavigateToUploadProgress = { navigator.navigate(UploadProgress) },
        )
    }
}

private fun EntryProviderScope<NavKey>.folderBrowserEntry(navigator: Navigator) {
    entry<FolderBrowser> { key ->
        val viewModel: FolderBrowserViewModel = hiltViewModel<FolderBrowserViewModel, FolderBrowserViewModel.Companion.Factory>(
            creationCallback = { factory: FolderBrowserViewModel.Companion.Factory ->
                factory.create(arguments = key)
            },
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle(initialValue = null)
        val uiStateValue = uiState.value ?: return@entry
        val context = LocalContext.current

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    is FolderBrowserViewModel.ViewModelEvent.PopBackStack -> {
                        navigator.goBack()
                    }

                    is FolderBrowserViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                        navigator.navigate(
                            FileBrowser(
                                displayPath = event.path,
                                fileId = event.fileId,
                            ),
                        )
                    }

                    is FolderBrowserViewModel.ViewModelEvent.NavigateToImageViewer -> {
                        navigator.navigate(
                            ImageViewer(
                                fileId = event.fileId,
                                allPaths = event.allPaths,
                            ),
                        )
                    }

                    is FolderBrowserViewModel.ViewModelEvent.NavigateToFolderBrowser -> {
                        navigator.navigate(
                            FolderBrowser(
                                fileId = event.fileId,
                                displayPath = event.displayPath,
                            ),
                        )
                    }

                    is FolderBrowserViewModel.ViewModelEvent.OpenWithExternalPlayer -> {
                        openWithExternalApp(
                            context = context,
                            viewSourceUri = event.viewSourceUri,
                            fileName = event.fileName,
                            mimeType = event.mimeType,
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
        val viewModel: ImageViewerViewModel = hiltViewModel<ImageViewerViewModel, ImageViewerViewModel.Companion.Factory>(
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

private fun EntryProviderScope<NavKey>.uploadProgressEntry(navigator: Navigator) {
    entry<UploadProgress> {
        val viewModel: UploadProgressViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    UploadProgressViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }

                    is UploadProgressViewModel.ViewModelEvent.NavigateToUploadDetail -> {
                        navigator.navigate(
                            UploadDetail(
                                workerId = event.workerId,
                            ),
                        )
                    }

                    is UploadProgressViewModel.ViewModelEvent.NavigateToPasteDetail -> {
                        navigator.navigate(
                            PasteDetail(jobId = event.jobId),
                        )
                    }

                    is UploadProgressViewModel.ViewModelEvent.NavigateToDeleteDetail -> {
                        navigator.navigate(
                            DeleteDetail(operationId = event.opId),
                        )
                    }

                    is UploadProgressViewModel.ViewModelEvent.NavigateToExtractDetail -> {
                        navigator.navigate(
                            ExtractDetail(operationId = event.opId),
                        )
                    }
                }
            }
        }

        UploadProgressScreen(
            uiState = uiState,
        )
    }
}

private fun EntryProviderScope<NavKey>.uploadDetailEntry(
    navigator: Navigator,
) {
    entry<UploadDetail> { key ->
        val viewModel: UploadDetailViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(key.workerId) {
            viewModel.init(key.workerId)
        }

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    UploadDetailViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }

                    is UploadDetailViewModel.ViewModelEvent.NavigateToDirectory -> {
                        navigator.navigate(
                            FileBrowser(
                                displayPath = event.displayPath.ifEmpty { null },
                                fileId = event.fileObjectId,
                            ),
                        )
                    }
                }
            }
        }

        val uiStateValue = uiState ?: return@entry
        UploadDetailScreen(uiState = uiStateValue)
    }
}

private fun EntryProviderScope<NavKey>.pasteDetailEntry(navigator: Navigator) {
    entry<PasteDetail> { key ->
        val viewModel: PasteDetailViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(key.jobId) {
            viewModel.init(key.jobId)
        }

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    PasteDetailViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }
                }
            }
        }

        val uiStateValue = uiState ?: return@entry
        PasteDetailScreen(uiState = uiStateValue)
    }
}

private fun EntryProviderScope<NavKey>.deleteDetailEntry(navigator: Navigator) {
    entry<DeleteDetail> { key ->
        val viewModel: DeleteDetailViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(key.operationId) {
            viewModel.init(key.operationId)
        }

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    DeleteDetailViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }
                }
            }
        }

        val uiStateValue = uiState ?: return@entry
        DeleteDetailScreen(uiState = uiStateValue)
    }
}

private fun EntryProviderScope<NavKey>.extractDetailEntry(navigator: Navigator) {
    entry<ExtractDetail> { key ->
        val viewModel: ExtractDetailViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val context = androidx.compose.ui.platform.LocalContext.current

        LaunchedEffect(key.operationId) {
            viewModel.init(key.operationId)
        }

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    ExtractDetailViewModel.ViewModelEvent.NavigateBack -> {
                        navigator.goBack()
                    }

                    is ExtractDetailViewModel.ViewModelEvent.NavigateToOutput -> {
                        navigator.navigate(
                            FileBrowser(
                                displayPath = event.displayPath,
                                fileId = event.fileId,
                            ),
                        )
                    }

                    is ExtractDetailViewModel.ViewModelEvent.OpenOutputFile -> {
                        openWithExternalApp(
                            context = context,
                            viewSourceUri = event.viewSourceUri,
                            fileName = event.fileName,
                            mimeType = event.mimeType,
                        )
                    }

                    is ExtractDetailViewModel.ViewModelEvent.OpenOutputFolder -> {
                        val relativePath = File(event.absolutePath)
                            .relativeToOrNull(android.os.Environment.getExternalStorageDirectory())
                            ?.path
                            .orEmpty()
                        val uri = android.provider.DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:$relativePath",
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                        }
                        runCatching {
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }

        val uiStateValue = uiState ?: return@entry
        ExtractDetailScreen(uiState = uiStateValue)
    }
}
