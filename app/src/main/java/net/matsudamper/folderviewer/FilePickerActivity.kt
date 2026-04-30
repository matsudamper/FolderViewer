package net.matsudamper.folderviewer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import java.io.File
import kotlinx.coroutines.launch
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.navigation.ExternalFilePicker
import net.matsudamper.folderviewer.navigation.ExternalFilePickerSelectedList
import net.matsudamper.folderviewer.navigation.Home
import net.matsudamper.folderviewer.navigation.ImageViewer
import net.matsudamper.folderviewer.navigation.Navigator
import net.matsudamper.folderviewer.navigation.rememberNavigationState
import net.matsudamper.folderviewer.navigation.toEntries
import net.matsudamper.folderviewer.repository.ExternalPickerRepository
import net.matsudamper.folderviewer.repository.ViewSourceUri
import net.matsudamper.folderviewer.ui.browser.FileBrowserUiState
import net.matsudamper.folderviewer.ui.browser.ImageViewerScreen
import net.matsudamper.folderviewer.ui.home.HomeScreen
import net.matsudamper.folderviewer.ui.picker.ExternalFilePickerScreen
import net.matsudamper.folderviewer.ui.picker.ExternalFilePickerSelectedListScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.viewmodel.browser.ImageViewerViewModel
import net.matsudamper.folderviewer.viewmodel.home.HomeViewModel
import net.matsudamper.folderviewer.viewmodel.picker.ExternalFilePickerSelectedListViewModel
import net.matsudamper.folderviewer.viewmodel.picker.ExternalFilePickerViewModel

@AndroidEntryPoint
class FilePickerActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var externalPickerRepository: ExternalPickerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(imageLoader)

        val allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)

        if (savedInstanceState == null) {
            externalPickerRepository.clear()
        }

        setContent {
            FolderViewerTheme {
                FilePickerContent(
                    allowMultiple = allowMultiple,
                    onReturnResult = { uris, mimeType ->
                        if (uris.isEmpty()) {
                            setResult(RESULT_CANCELED)
                            finish()
                            return@FilePickerContent
                        }
                        val resultIntent = Intent()
                        if (uris.size == 1) {
                            resultIntent.data = uris.first()
                            if (mimeType != null) resultIntent.type = mimeType
                        } else {
                            val clipData = ClipData.newRawUri(null, uris.first())
                            uris.drop(1).forEach { clipData.addItem(ClipData.Item(it)) }
                            resultIntent.clipData = clipData
                        }
                        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun FilePickerContent(
    allowMultiple: Boolean,
    onReturnResult: (List<Uri>, String?) -> Unit,
    onCancel: () -> Unit,
) {
    val navigationState = rememberNavigationState(
        startRoute = Home,
        topLevelRoutes = setOf(Home),
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val entryProvider = remember(navigator) {
        entryProvider(navigator, allowMultiple, onReturnResult)
    }

    NavDisplay(
        entries = navigationState.toEntries(
            entryProvider = entryProvider,
            viewModelStoreOwner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        ),
        onBack = {
            val currentStack = navigationState.backStacks[navigationState.topLevelRoute]
            if (currentStack == null || currentStack.size <= 1) {
                onCancel()
            } else {
                navigator.goBack()
            }
        },
    )
}

private fun entryProvider(
    navigator: Navigator,
    allowMultiple: Boolean,
    onReturnResult: (List<Uri>, String?) -> Unit,
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        pickerHomeEntry(navigator, allowMultiple)
        externalFilePickerEntry(navigator, onReturnResult)
        externalFilePickerSelectedListEntry(navigator)
        imageViewerEntry(navigator)
    }
}

private fun EntryProviderScope<NavKey>.pickerHomeEntry(
    navigator: Navigator,
    allowMultiple: Boolean,
) {
    entry<Home> {
        val viewModel: HomeViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    is HomeViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                        navigator.navigate(
                            ExternalFilePicker(
                                allowMultiple = allowMultiple,
                                displayPath = null,
                                fileId = FileObjectId.Root(storageId = event.storageId),
                            ),
                        )
                    }

                    HomeViewModel.ViewModelEvent.NavigateToSettings,
                    HomeViewModel.ViewModelEvent.NavigateToUploadProgress,
                    HomeViewModel.ViewModelEvent.NavigateToStorageTypeSelection,
                    is HomeViewModel.ViewModelEvent.NavigateToSmbAdd,
                    is HomeViewModel.ViewModelEvent.NavigateToSharePointAdd,
                    -> Unit
                }
            }
        }

        HomeScreen(uiState = uiState)
    }
}

@Composable
private fun ExternalFilePickerEventHandler(
    viewModel: ExternalFilePickerViewModel,
    navigator: Navigator,
    allowMultiple: Boolean,
    onReturnResult: (List<Uri>, String?) -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel.viewModelEventFlow) {
        viewModel.viewModelEventFlow.collect { event ->
            when (event) {
                ExternalFilePickerViewModel.ViewModelEvent.PopBackStack -> navigator.goBack()

                is ExternalFilePickerViewModel.ViewModelEvent.NavigateToExternalFilePicker -> {
                    navigator.navigate(
                        ExternalFilePicker(
                            allowMultiple = allowMultiple,
                            displayPath = event.displayPath,
                            fileId = event.fileId,
                        ),
                    )
                }

                ExternalFilePickerViewModel.ViewModelEvent.NavigateToSelectedList -> {
                    navigator.navigate(ExternalFilePickerSelectedList)
                }

                is ExternalFilePickerViewModel.ViewModelEvent.NavigateToImageViewer -> {
                    navigator.navigate(
                        ImageViewer(fileId = event.fileId, allPaths = event.allPaths),
                    )
                }

                is ExternalFilePickerViewModel.ViewModelEvent.OpenWithExternalPlayer -> {
                    val uri = buildUri(context, event.viewSourceUri, event.fileId, event.fileName)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, event.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }

                is ExternalFilePickerViewModel.ViewModelEvent.ReturnSingleResult -> {
                    val uri = buildUri(context, event.viewSourceUri, event.fileId, event.fileName)
                    onReturnResult(listOf(uri), event.mimeType)
                }

                is ExternalFilePickerViewModel.ViewModelEvent.ReturnMultipleResults -> {
                    val uris = event.items.map { item ->
                        buildUri(context, item.viewSourceUri, item.fileId, item.fileName)
                    }
                    val mimeType = event.items.mapNotNull { it.mimeType }.distinct()
                        .let { types -> if (types.size == 1) types.first() else "*/*" }
                    onReturnResult(uris, mimeType.ifEmpty { null })
                }
            }
        }
    }
}

private fun EntryProviderScope<NavKey>.externalFilePickerEntry(
    navigator: Navigator,
    onReturnResult: (List<Uri>, String?) -> Unit,
) {
    entry<ExternalFilePicker> { key ->
        val viewModel: ExternalFilePickerViewModel =
            hiltViewModel<ExternalFilePickerViewModel, ExternalFilePickerViewModel.Companion.Factory>(
                creationCallback = { factory -> factory.create(arguments = key) },
            )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle(initialValue = null)
        val uiStateValue = uiState.value ?: return@entry

        ExternalFilePickerEventHandler(
            viewModel = viewModel,
            navigator = navigator,
            allowMultiple = key.allowMultiple,
            onReturnResult = onReturnResult,
        )

        ExternalFilePickerScreen(
            uiState = uiStateValue,
            uiEvent = viewModel.uiEvent,
        )
    }
}

@Composable
private fun ExternalFilePickerSelectedListEventHandler(
    viewModel: ExternalFilePickerSelectedListViewModel,
    navigator: Navigator,
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel.viewModelEventFlow) {
        viewModel.viewModelEventFlow.collect { event ->
            when (event) {
                ExternalFilePickerSelectedListViewModel.ViewModelEvent.PopBackStack -> {
                    navigator.goBack()
                }

                is ExternalFilePickerSelectedListViewModel.ViewModelEvent.NavigateToImageViewer -> {
                    navigator.navigate(
                        ImageViewer(fileId = event.fileId, allPaths = event.allPaths),
                    )
                }

                is ExternalFilePickerSelectedListViewModel.ViewModelEvent.OpenWithExternalPlayer -> {
                    val uri = buildUri(context, event.viewSourceUri, event.fileId, event.fileName)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, event.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }
}

private fun EntryProviderScope<NavKey>.externalFilePickerSelectedListEntry(
    navigator: Navigator,
) {
    entry<ExternalFilePickerSelectedList> {
        val viewModel: ExternalFilePickerSelectedListViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        ExternalFilePickerSelectedListEventHandler(
            viewModel = viewModel,
            navigator = navigator,
        )

        ExternalFilePickerSelectedListScreen(uiState = uiState)
    }
}

private fun EntryProviderScope<NavKey>.imageViewerEntry(navigator: Navigator) {
    entry<ImageViewer> { key ->
        val viewModel: ImageViewerViewModel =
            hiltViewModel<ImageViewerViewModel, ImageViewerViewModel.Companion.Factory>(
                creationCallback = { factory -> factory.create(arguments = key) },
            )
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    is ImageViewerViewModel.ViewModelEvent.PopBackStack -> navigator.goBack()
                }
            }
        }

        ImageViewerScreen(uiState = uiState)
    }
}

private fun buildUri(
    context: android.content.Context,
    viewSourceUri: ViewSourceUri,
    fileId: FileObjectId.Item,
    fileName: String,
): Uri {
    return when (viewSourceUri) {
        is ViewSourceUri.LocalFile -> {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(viewSourceUri.path),
            )
        }

        is ViewSourceUri.RemoteUrl -> viewSourceUri.url.toUri()

        is ViewSourceUri.StreamProvider -> {
            StreamingContentProvider.buildUri(fileId = fileId, fileName = fileName)
        }
    }
}
