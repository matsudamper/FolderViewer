package net.matsudamper.folderviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import net.matsudamper.folderviewer.navigation.DeleteDetail
import net.matsudamper.folderviewer.navigation.Navigator
import net.matsudamper.folderviewer.navigation.PasteDetail
import net.matsudamper.folderviewer.navigation.UploadDetail
import net.matsudamper.folderviewer.navigation.rememberNavigationState
import net.matsudamper.folderviewer.navigation.toEntries
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme

@AndroidEntryPoint
class OperationDetailActivity : ComponentActivity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(imageLoader)

        val startRoute = createStartRoute(intent)
        if (startRoute == null) {
            finish()
            return
        }

        setContent {
            FolderViewerTheme {
                val navigationState = rememberNavigationState(
                    startRoute = startRoute,
                    topLevelRoutes = setOf(startRoute),
                )
                val navigator = remember(navigationState) {
                    Navigator(navigationState, onExit = { finish() })
                }
                val entryProvider = remember(navigator) { entryProvider(navigator) }
                val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)

                NavDisplay(
                    modifier = Modifier.fillMaxSize(),
                    entries = navigationState.toEntries(
                        entryProvider = entryProvider,
                        viewModelStoreOwner = viewModelStoreOwner,
                    ),
                    onBack = { navigator.goBack() },
                )
            }
        }
    }

    private fun createStartRoute(intent: Intent): NavKey? {
        return when (intent.getStringExtra(EXTRA_OPERATION_TYPE)) {
            OPERATION_TYPE_UPLOAD -> {
                val workerId = intent.getStringExtra(EXTRA_WORKER_ID) ?: return null
                UploadDetail(workerId = workerId)
            }

            OPERATION_TYPE_PASTE -> {
                val jobId = intent.getLongExtra(EXTRA_OPERATION_ID, -1L)
                if (jobId == -1L) return null
                PasteDetail(jobId = jobId)
            }

            OPERATION_TYPE_DELETE -> {
                val operationId = intent.getLongExtra(EXTRA_OPERATION_ID, -1L)
                if (operationId == -1L) return null
                DeleteDetail(operationId = operationId)
            }

            else -> null
        }
    }

    companion object {
        private const val EXTRA_OPERATION_TYPE = "operation_type"
        private const val EXTRA_OPERATION_ID = "operation_id"
        private const val EXTRA_WORKER_ID = "worker_id"
        private const val OPERATION_TYPE_UPLOAD = "upload"
        private const val OPERATION_TYPE_PASTE = "paste"
        private const val OPERATION_TYPE_DELETE = "delete"

        fun createUploadDetailIntent(context: Context, workerId: String): Intent {
            return Intent(context, OperationDetailActivity::class.java).apply {
                data = "folderviewer://operation/$OPERATION_TYPE_UPLOAD/$workerId".toUri()
                putExtra(EXTRA_OPERATION_TYPE, OPERATION_TYPE_UPLOAD)
                putExtra(EXTRA_WORKER_ID, workerId)
            }
        }

        fun createPasteDetailIntent(context: Context, jobId: Long): Intent {
            return Intent(context, OperationDetailActivity::class.java).apply {
                data = "folderviewer://operation/$OPERATION_TYPE_PASTE/$jobId".toUri()
                putExtra(EXTRA_OPERATION_TYPE, OPERATION_TYPE_PASTE)
                putExtra(EXTRA_OPERATION_ID, jobId)
            }
        }

        fun createDeleteDetailIntent(context: Context, operationId: Long): Intent {
            return Intent(context, OperationDetailActivity::class.java).apply {
                data = "folderviewer://operation/$OPERATION_TYPE_DELETE/$operationId".toUri()
                putExtra(EXTRA_OPERATION_TYPE, OPERATION_TYPE_DELETE)
                putExtra(EXTRA_OPERATION_ID, operationId)
            }
        }
    }
}
