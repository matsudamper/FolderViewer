package net.matsudamper.folderviewer

import android.content.ClipData
import android.content.ComponentName
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import net.matsudamper.folderviewer.viewmodel.extract.ExternalIncomingUriResolution

@AndroidEntryPoint
class ExternalExtractActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    private var viewModelArgs by mutableStateOf<ExternalExtractLaunchArgs?>(null)
    private var launchErrorMessage by mutableStateOf<String?>(null)
    private var handledIncomingUri by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(imageLoader)

        val uri = extractTargetUri(intent)
        if (uri == null) {
            launchErrorMessage = "ファイルを開けませんでした"
        } else {
            lifecycleScope.launch {
                val resolved = withContext(Dispatchers.IO) {
                    ExternalExtractIntentHandler.resolve(
                        context = this@ExternalExtractActivity,
                        uri = uri,
                        mimeType = intent.type,
                    )
                }
                when (resolved) {
                    is ExternalIncomingUriResolution.Extractable -> {
                        requestNotificationPermissionIfNeeded()
                        viewModelArgs = resolved.args
                    }

                    is ExternalIncomingUriResolution.Directory -> {
                        val launched = openDirectoryWithChooser(resolved.uri, resolved.mimeType)
                        if (launched) {
                            handledIncomingUri = true
                            finish()
                        } else {
                            launchErrorMessage = "フォルダを開けませんでした"
                        }
                    }

                    ExternalIncomingUriResolution.Unsupported -> {
                        launchErrorMessage = "対応していないファイルです"
                    }
                }
            }
        }

        setContent {
            FolderViewerTheme {
                val launchError = launchErrorMessage
                if (launchError != null) {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("エラー") },
                        text = { Text(launchError) },
                        confirmButton = {
                            TextButton(onClick = { finish() }) {
                                Text("OK")
                            }
                        },
                    )
                    return@FolderViewerTheme
                }

                val args = viewModelArgs
                if (args == null && !handledIncomingUri) {
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
                )

                androidx.compose.runtime.LaunchedEffect(viewModel) {
                    viewModel.viewModelEventFlow.collect { event ->
                        when (event) {
                            ExternalExtractViewModel.ViewModelEvent.Finish -> finish()

                            is ExternalExtractViewModel.ViewModelEvent.OpenExtractDetail -> {
                                startActivity(
                                    OperationDetailActivity.createExtractDetailIntent(
                                        this@ExternalExtractActivity,
                                        event.jobId,
                                    ),
                                )
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

    private fun openDirectoryWithChooser(uri: Uri, mimeType: String): Boolean {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        forwardUriGrantFlags(source = intent, target = viewIntent)
        val chooserIntent = Intent.createChooser(viewIntent, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@ExternalExtractActivity, ExternalExtractActivity::class.java)),
            )
        }
        forwardUriGrantFlags(source = intent, target = chooserIntent)
        return runCatching {
            startActivity(chooserIntent)
        }.isSuccess
    }

    private fun forwardUriGrantFlags(source: Intent, target: Intent) {
        if (source.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (source.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
            target.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (source.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0) {
            target.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
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
