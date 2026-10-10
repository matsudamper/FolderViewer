package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TextViewerScreenPreview() {
    val text = """
        fun main() {
            println("hello")
        }

        fun greet(name: String) {
            println(name)
        }
    """.trimIndent()
    val matches = Regex("fun").findAll(text).map { result ->
        TextViewerUiState.Match(
            start = result.range.first,
            endExclusive = result.range.last + 1,
        )
    }.toList()
    FolderViewerTheme(dynamicColor = false, darkTheme = false) {
        TextViewerScreen(
            uiState = TextViewerUiState(
                title = "Main.kt",
                body = TextViewerUiState.Body.Text(text),
                searchQuery = "fun",
                matchCase = false,
                useRegex = true,
                patternInvalid = false,
                matches = matches,
                currentMatchIndex = 0,
                focusToken = 0,
                callbacks = object : TextViewerUiState.Callbacks {
                    override fun onBack() = Unit
                    override fun onSearchQueryChange(query: String) = Unit
                    override fun onMatchCaseChange(matchCase: Boolean) = Unit
                    override fun onUseRegexChange(useRegex: Boolean) = Unit
                    override fun onNextMatch() = Unit
                    override fun onPreviousMatch() = Unit
                },
            ),
        )
    }
}
