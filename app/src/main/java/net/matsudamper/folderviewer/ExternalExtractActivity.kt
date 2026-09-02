package net.matsudamper.folderviewer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.extract.ExternalExtractScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.ui.util.showDismissibleSnackbar
import net.matsudamper.folderviewer.viewmodel.extract.ExternalExtractIntentHandler
import net.matsudamper.folderviewer.viewmodel.extract.ExternalExtractLaunchArgs
import net.matsudamper.folderviewer.viewmodel.extract.ExternalExtractViewModel

@AndroidEntryPoint
class ExternalExtractActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    private var viewModelArgs by mutableStateOf<ExternalExtractLaunchArgs?>(null)
    private var launchErrorMessage by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(imageLoader)
        requestNotificationPermissionIfNeeded()

        val uri = extractTargetUri(intent)
        if (uri == null) {
            launchErrorMessage = "ファイルを開けませんでした"
        } else {
            lifecycleScope.launch {
                val resolved = withContext(Dispatchers.IO) {
                    ExternalExtractIntentHandler.resolveLaunchArgs(this@ExternalExtractActivity, uri)
                }
                if (resolved == null) {
                    launchErrorMessage = "対応していないファイルです"
                } else {
                    viewModelArgs = resolved
                }
            }
        }

        setContent {
            FolderViewerTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val detailActionLabel = stringResource(R.string.snackbar_action_detail)

                androidx.compose.runtime.LaunchedEffect(launchErrorMessage) {
                    val message = launchErrorMessage ?: return@LaunchedEffect
                    snackbarHostState.showDismissibleSnackbar(message = message)
                    finish()
                }

                val args = viewModelArgs
                if (args == null && launchErrorMessage == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    return@FolderViewerTheme
                }
                if (args == null) {
                    return@FolderViewerTheme
                }
                val viewModel = hiltViewModel<ExternalExtractViewModel, ExternalExtractViewModel.Companion.Factory>(
                    creationCallback = { factory -> factory.create(args) },
                )
                val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
                ExternalExtractScreen(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                )

                androidx.compose.runtime.LaunchedEffect(viewModel) {
                    viewModel.viewModelEventFlow.collect { event ->
                        when (event) {
                            ExternalExtractViewModel.ViewModelEvent.Finish -> finish()

                            is ExternalExtractViewModel.ViewModelEvent.ShowSnackbar -> {
                                val extractDetailJobId = event.extractDetailJobId
                                val result = snackbarHostState.showDismissibleSnackbar(
                                    message = event.message,
                                    actionLabel = extractDetailJobId?.let { detailActionLabel },
                                )
                                if (
                                    result == SnackbarResult.ActionPerformed &&
                                    extractDetailJobId != null
                                ) {
                                    startActivity(
                                        OperationDetailActivity.createExtractDetailIntent(
                                            this@ExternalExtractActivity,
                                            extractDetailJobId,
                                        ),
                                    )
                                }
                                if (event.finishAfterDismiss) {
                                    finish()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractTargetUri(intent: Intent): Uri? {
        intent.data?.let { return it }
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
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
