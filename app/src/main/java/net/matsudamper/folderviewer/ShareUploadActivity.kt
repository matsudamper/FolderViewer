package net.matsudamper.folderviewer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import net.matsudamper.folderviewer.common.FileObjectId
import net.matsudamper.folderviewer.navigation.Home
import net.matsudamper.folderviewer.navigation.Navigator
import net.matsudamper.folderviewer.navigation.ShareUploadDestination
import net.matsudamper.folderviewer.navigation.rememberNavigationState
import net.matsudamper.folderviewer.navigation.toEntries
import net.matsudamper.folderviewer.repository.ShareUploadRepository
import net.matsudamper.folderviewer.ui.home.StoragePickerScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.ui.upload.ShareUploadDestinationScreen
import net.matsudamper.folderviewer.viewmodel.home.StoragePickerViewModel
import net.matsudamper.folderviewer.viewmodel.upload.ShareUploadDestinationViewModel

@AndroidEntryPoint
class ShareUploadActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var shareUploadRepository: ShareUploadRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(imageLoader)

        val uris = extractSharedUris(intent)
        if (uris.isEmpty()) {
            finish()
            return
        }

        requestNotificationPermissionIfNeeded()
        loadPendingFiles(uris)

        setContent {
            FolderViewerTheme {
                ShareUploadContent(
                    onFinish = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun extractSharedUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { listOf(it) }
                    .orEmpty()
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
            }

            else -> emptyList()
        }
    }

    private fun loadPendingFiles(uris: List<Uri>) {
        lifecycleScope.launch {
            val pendingFiles = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val fileName = DocumentFile.fromSingleUri(this@ShareUploadActivity, uri)?.name
                        ?: "shared_file"
                    ShareUploadRepository.PendingFile(uri = uri, fileName = fileName)
                }
            }
            shareUploadRepository.setPendingFiles(pendingFiles)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun ShareUploadContent(
    onFinish: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val navigationState = rememberNavigationState(
        startRoute = Home,
        topLevelRoutes = setOf(Home),
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val entryProvider = remember(navigator) { entryProvider(navigator, onFinish) }

    NavDisplay(
        entries = navigationState.toEntries(
            entryProvider = entryProvider,
            viewModelStoreOwner = LocalViewModelStoreOwner.current!!,
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
    onFinish: (String) -> Unit,
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        storagePickerEntry(navigator)
        shareUploadDestinationEntry(navigator, onFinish)
    }
}

private fun EntryProviderScope<NavKey>.storagePickerEntry(navigator: Navigator) {
    entry<Home> {
        val viewModel: StoragePickerViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    is StoragePickerViewModel.ViewModelEvent.NavigateToFileBrowser -> {
                        navigator.navigate(
                            ShareUploadDestination(
                                displayPath = null,
                                fileId = FileObjectId.Root(storageId = event.storageId),
                            ),
                        )
                    }
                }
            }
        }

        StoragePickerScreen(uiState = uiState)
    }
}

private fun EntryProviderScope<NavKey>.shareUploadDestinationEntry(
    navigator: Navigator,
    onFinish: (String) -> Unit,
) {
    entry<ShareUploadDestination> { key ->
        val viewModel: ShareUploadDestinationViewModel =
            hiltViewModel<ShareUploadDestinationViewModel, ShareUploadDestinationViewModel.Companion.Factory>(
                creationCallback = { factory -> factory.create(arguments = key) },
            )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle(initialValue = null)
        val uiStateValue = uiState.value ?: return@entry

        LaunchedEffect(viewModel.viewModelEventFlow) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    ShareUploadDestinationViewModel.ViewModelEvent.PopBackStack -> navigator.goBack()

                    is ShareUploadDestinationViewModel.ViewModelEvent.NavigateToDestination -> {
                        navigator.navigate(
                            ShareUploadDestination(
                                displayPath = event.displayPath,
                                fileId = event.fileId,
                            ),
                        )
                    }

                    is ShareUploadDestinationViewModel.ViewModelEvent.FinishWithMessage -> {
                        onFinish(event.message)
                    }
                }
            }
        }

        ShareUploadDestinationScreen(uiState = uiStateValue)
    }
}
