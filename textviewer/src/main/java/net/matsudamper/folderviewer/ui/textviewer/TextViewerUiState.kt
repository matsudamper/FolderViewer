package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.runtime.Immutable

data class TextViewerUiState(
    val title: String,
    val body: Body,
    val searchQuery: String,
    val matchCase: Boolean,
    val useRegex: Boolean,
    val patternInvalid: Boolean,
    val matches: List<Match>,
    val currentMatchIndex: Int,
    val focusToken: Int,
    val encodingMenu: EncodingMenu?,
    val lineEndingLabel: String?,
    val showLineNumbers: Boolean,
    val callbacks: Callbacks,
) {
    data class EncodingMenu(
        val currentLabel: String,
        val choices: List<Choice>,
    ) {
        data class Choice(
            val label: String,
            val selected: Boolean,
        )
    }
    data class Match(
        val start: Int,
        val endExclusive: Int,
    )

    sealed interface Body {
        data object Loading : Body

        data class Text(val text: String) : Body

        data class Failure(val reason: Reason) : Body
    }

    enum class Reason {
        TooLarge,
        Binary,
        Unreadable,
    }

    @Immutable
    interface Callbacks {
        fun onBack()

        fun onSearchQueryChange(query: String)

        fun onMatchCaseChange(matchCase: Boolean)

        fun onUseRegexChange(useRegex: Boolean)

        fun onNextMatch()

        fun onPreviousMatch()

        fun onEncodingSelected(label: String)

        fun onShowLineNumbersChange(show: Boolean)
    }
}
