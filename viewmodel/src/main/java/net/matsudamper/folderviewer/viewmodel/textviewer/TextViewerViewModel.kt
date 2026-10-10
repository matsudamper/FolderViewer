package net.matsudamper.folderviewer.viewmodel.textviewer

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.textviewer.TextViewerUiState
import net.matsudamper.folderviewer.viewmodel.util.TextFileDecoder
import net.matsudamper.folderviewer.viewmodel.util.TextLineEnding
import net.matsudamper.folderviewer.viewmodel.util.TextLineEndingDetector
import net.matsudamper.folderviewer.viewmodel.util.TextSearchMatcher

data class TextViewerLaunchArgs(
    val uri: String,
)

@HiltViewModel(assistedFactory = TextViewerViewModel.Companion.Factory::class)
class TextViewerViewModel @AssistedInject constructor(
    @Assisted private val args: TextViewerLaunchArgs,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val viewModelEventChannel = Channel<ViewModelEvent>(Channel.UNLIMITED)
    val viewModelEventFlow = viewModelEventChannel.receiveAsFlow()

    private val callbacks = object : TextViewerUiState.Callbacks {
        override fun onBack() {
            viewModelEventChannel.trySend(ViewModelEvent.Finish)
        }

        override fun onSearchQueryChange(query: String) {
            state.update { it.copy(searchQuery = query) }
        }

        override fun onMatchCaseChange(matchCase: Boolean) {
            state.update { it.copy(matchCase = matchCase) }
        }

        override fun onUseRegexChange(useRegex: Boolean) {
            state.update { it.copy(useRegex = useRegex) }
        }

        override fun onNextMatch() {
            moveMatch(step = 1)
        }

        override fun onPreviousMatch() {
            moveMatch(step = -1)
        }

        override fun onEncodingSelected(label: String) {
            val encoding = TextFileDecoder.availableEncodings().find { it.label == label } ?: return
            val bytes = state.value.bytes ?: return
            viewModelScope.launch {
                val decoded = withContext(Dispatchers.Default) {
                    val text = TextFileDecoder.decodeWith(bytes.value, encoding)
                    DecodedText(text = text, lineEnding = TextLineEndingDetector.detect(text))
                }
                state.update { current ->
                    if (current.bytes !== bytes) return@update current
                    current.copy(
                        body = TextViewerUiState.Body.Text(decoded.text),
                        loadedText = decoded.text,
                        encoding = encoding,
                        lineEnding = decoded.lineEnding,
                        matches = listOf(),
                        currentMatchIndex = -1,
                        patternInvalid = false,
                    )
                }
            }
        }
    }

    private val state = MutableStateFlow(
        ViewerState(
            title = context.getString(R.string.text_viewer_default_title),
            body = TextViewerUiState.Body.Loading,
            loadedText = null,
            searchQuery = "",
            matchCase = false,
            useRegex = false,
            patternInvalid = false,
            matches = listOf(),
            currentMatchIndex = -1,
            focusToken = 0,
            bytes = null,
            encoding = null,
            lineEnding = null,
        ),
    )

    val uiState: StateFlow<TextViewerUiState> = state
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = state.value.toUiState(),
        )

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                TextViewerDocumentLoader(
                    contentResolver = context.contentResolver,
                    defaultTitle = context.getString(R.string.text_viewer_default_title),
                ).load(args.uri.toUri())
            }
            state.update { current -> current.withLoadResult(loaded) }
        }
        viewModelScope.launch {
            state
                .map { current ->
                    SearchRequest(
                        text = current.loadedText,
                        query = current.searchQuery,
                        matchCase = current.matchCase,
                        useRegex = current.useRegex,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { request ->
                    applySearch(request)
                }
        }
    }

    private suspend fun applySearch(request: SearchRequest) {
        val text = request.text ?: return
        if (request.query.isEmpty()) {
            state.update { current ->
                if (!current.matchesRequest(request)) return@update current
                current.copy(
                    patternInvalid = false,
                    matches = listOf(),
                    currentMatchIndex = -1,
                )
            }
            return
        }
        delay(SearchDelayMillis)
        val result = withContext(Dispatchers.Default) {
            TextSearchMatcher.find(
                text = text,
                query = request.query,
                useRegex = request.useRegex,
                matchCase = request.matchCase,
            )
        }
        state.update { current ->
            if (!current.matchesRequest(request)) return@update current
            when (result) {
                TextSearchMatcher.Result.InvalidPattern -> current.copy(
                    patternInvalid = true,
                    matches = listOf(),
                    currentMatchIndex = -1,
                )

                is TextSearchMatcher.Result.Matches -> {
                    val matches = result.ranges.map { range ->
                        TextViewerUiState.Match(
                            start = range.start,
                            endExclusive = range.endExclusive,
                        )
                    }
                    current.copy(
                        patternInvalid = false,
                        matches = matches,
                        currentMatchIndex = if (matches.isEmpty()) -1 else 0,
                        focusToken = if (matches.isEmpty()) current.focusToken else current.focusToken + 1,
                    )
                }
            }
        }
    }

    private fun moveMatch(step: Int) {
        state.update { current ->
            val count = current.matches.size
            if (count == 0) return@update current
            val nextIndex = if (current.currentMatchIndex < 0) {
                if (step >= 0) 0 else count - 1
            } else {
                val remainder = (current.currentMatchIndex + step) % count
                if (remainder < 0) remainder + count else remainder
            }
            current.copy(
                currentMatchIndex = nextIndex,
                focusToken = current.focusToken + 1,
            )
        }
    }

    private fun ViewerState.toUiState(): TextViewerUiState {
        return TextViewerUiState(
            title = title,
            body = body,
            searchQuery = searchQuery,
            matchCase = matchCase,
            useRegex = useRegex,
            patternInvalid = patternInvalid,
            matches = matches,
            currentMatchIndex = currentMatchIndex,
            focusToken = focusToken,
            encodingMenu = encodingMenu(),
            lineEndingLabel = lineEnding?.label(),
            callbacks = callbacks,
        )
    }

    private fun ViewerState.encodingMenu(): TextViewerUiState.EncodingMenu? {
        if (bytes == null) return null
        val choices = TextFileDecoder.availableEncodings().map { encodingOption ->
            TextViewerUiState.EncodingMenu.Choice(
                label = encodingOption.label,
                selected = encodingOption == encoding,
            )
        }
        return TextViewerUiState.EncodingMenu(
            currentLabel = encoding?.label ?: context.getString(R.string.text_viewer_encoding),
            choices = choices,
        )
    }

    private fun ViewerState.withLoadResult(loaded: TextViewerDocumentLoader.Result): ViewerState {
        return when (loaded) {
            is TextViewerDocumentLoader.Result.Success -> {
                val text = loaded.text
                if (text == null) {
                    copy(
                        title = loaded.title,
                        body = TextViewerUiState.Body.Failure(TextViewerUiState.Reason.Binary),
                        loadedText = null,
                        bytes = loaded.bytes,
                        encoding = null,
                        lineEnding = null,
                    )
                } else {
                    copy(
                        title = loaded.title,
                        body = TextViewerUiState.Body.Text(text),
                        loadedText = text,
                        bytes = loaded.bytes,
                        encoding = loaded.encoding,
                        lineEnding = loaded.lineEnding,
                    )
                }
            }

            is TextViewerDocumentLoader.Result.Failure -> copy(
                title = loaded.title,
                body = TextViewerUiState.Body.Failure(loaded.reason.toUiReason()),
                loadedText = null,
                bytes = null,
                encoding = null,
                lineEnding = null,
            )
        }
    }

    private fun ViewerState.matchesRequest(request: SearchRequest): Boolean {
        return loadedText == request.text &&
            searchQuery == request.query &&
            matchCase == request.matchCase &&
            useRegex == request.useRegex
    }

    private fun TextLineEnding.label(): String {
        return when (this) {
            TextLineEnding.Lf -> context.getString(R.string.text_viewer_line_ending_lf)
            TextLineEnding.CrLf -> context.getString(R.string.text_viewer_line_ending_crlf)
            TextLineEnding.Cr -> context.getString(R.string.text_viewer_line_ending_cr)
            TextLineEnding.Mixed -> context.getString(R.string.text_viewer_line_ending_mixed)
        }
    }

    private fun TextViewerDocumentLoader.Result.Reason.toUiReason(): TextViewerUiState.Reason {
        return when (this) {
            TextViewerDocumentLoader.Result.Reason.TooLarge -> TextViewerUiState.Reason.TooLarge
            TextViewerDocumentLoader.Result.Reason.Unreadable -> TextViewerUiState.Reason.Unreadable
        }
    }

    private data class ViewerState(
        val title: String,
        val body: TextViewerUiState.Body,
        val loadedText: String?,
        val searchQuery: String,
        val matchCase: Boolean,
        val useRegex: Boolean,
        val patternInvalid: Boolean,
        val matches: List<TextViewerUiState.Match>,
        val currentMatchIndex: Int,
        val focusToken: Int,
        val bytes: TextFileDecoder.FileBytes?,
        val encoding: TextFileDecoder.TextEncoding?,
        val lineEnding: TextLineEnding?,
    )

    private data class DecodedText(
        val text: String,
        val lineEnding: TextLineEnding?,
    )

    private data class SearchRequest(
        val text: String?,
        val query: String,
        val matchCase: Boolean,
        val useRegex: Boolean,
    )

    sealed interface ViewModelEvent {
        data object Finish : ViewModelEvent
    }

    companion object {
        private const val SearchDelayMillis: Long = 150

        @AssistedFactory
        interface Factory {
            fun create(args: TextViewerLaunchArgs): TextViewerViewModel
        }
    }
}
