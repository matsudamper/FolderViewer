package net.matsudamper.folderviewer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import net.matsudamper.folderviewer.ui.extract.ExternalExtractScreen
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
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
                androidx.compose.runtime.LaunchedEffect(launchErrorMessage) {
                    if (launchErrorMessage != null) {
                        finish()
                    }
                }

                val args = viewModelArgs
                if (args == null && launchErrorMessage == null) {
                    Box(modifier = Modifier.fillMaxSize())
                    return@FolderViewerTheme
                }
                if (args == null) {
                    return@FolderViewerTheme
                }
                val viewModel = hiltViewModel<ExternalExtractViewModel, ExternalExtractViewModel.Companion.Factory>(
                    creationCallback = { factory -> factory.create(args) },
                )
                val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
                ExternalExtractScreen(uiState = uiState)

                androidx.compose.runtime.LaunchedEffect(viewModel) {
                    viewModel.viewModelEventFlow.collect { event ->
                        when (event) {
                            ExternalExtractViewModel.ViewModelEvent.Finish -> finish()
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
