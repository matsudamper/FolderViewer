package net.matsudamper.folderviewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import net.matsudamper.folderviewer.textviewer.R
import net.matsudamper.folderviewer.ui.textviewer.TextViewerScreen
import net.matsudamper.folderviewer.ui.textviewer.TextViewerUiState
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.viewmodel.textviewer.TextViewerLaunchArgs
import net.matsudamper.folderviewer.viewmodel.textviewer.TextViewerViewModel

@AndroidEntryPoint
class TextViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.data
        setContent {
            FolderViewerTheme {
                if (uri == null) {
                    MissingUriTextViewer()
                } else {
                    TextViewerRoute(uri = uri)
                }
            }
        }
    }

    @Composable
    private fun MissingUriTextViewer() {
        val title = stringResource(R.string.text_viewer_default_title)
        val uiState = remember(title) {
            TextViewerUiState(
                title = title,
                body = TextViewerUiState.Body.Failure(TextViewerUiState.Reason.Unreadable),
                searchQuery = "",
                matchCase = false,
                useRegex = false,
                patternInvalid = false,
                matches = listOf(),
                currentMatchIndex = -1,
                focusToken = 0,
                encodingMenu = null,
                lineEndingLabel = null,
                showLineNumbers = false,
                callbacks = object : TextViewerUiState.Callbacks {
                    override fun onBack() {
                        finish()
                    }

                    override fun onSearchQueryChange(query: String) = Unit

                    override fun onMatchCaseChange(matchCase: Boolean) = Unit

                    override fun onUseRegexChange(useRegex: Boolean) = Unit

                    override fun onNextMatch() = Unit

                    override fun onPreviousMatch() = Unit

                    override fun onEncodingSelected(label: String) = Unit

                    override fun onShowLineNumbersChange(show: Boolean) = Unit
                },
            )
        }
        TextViewerScreen(uiState = uiState)
    }

    @Composable
    private fun TextViewerRoute(uri: Uri) {
        val viewModel = hiltViewModel<TextViewerViewModel, TextViewerViewModel.Companion.Factory>(
            creationCallback = { factory ->
                factory.create(TextViewerLaunchArgs(uri = uri.toString()))
            },
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
        LaunchedEffect(viewModel) {
            viewModel.viewModelEventFlow.collect { event ->
                when (event) {
                    TextViewerViewModel.ViewModelEvent.Finish -> finish()
                }
            }
        }
        TextViewerScreen(uiState = uiState)
    }
}
