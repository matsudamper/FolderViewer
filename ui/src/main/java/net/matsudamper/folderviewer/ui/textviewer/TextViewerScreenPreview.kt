package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme

private const val PreviewText = """
fun main() {
    println("hello")
}

fun greet(name: String) {
    println(name)
}
"""

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TextViewerScreenCollapsedPreview() {
    TextViewerPreview(
        searchQuery = "",
        useRegex = false,
        matches = listOf(),
        currentMatchIndex = -1,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TextViewerScreenPreview() {
    val text = PreviewText.trimIndent()
    val matches = Regex("fun").findAll(text).map { result ->
        TextViewerUiState.Match(
            start = result.range.first,
            endExclusive = result.range.last + 1,
        )
    }.toList()
    TextViewerPreview(
        searchQuery = "fun",
        useRegex = true,
        matches = matches,
        currentMatchIndex = 0,
    )
}

@Composable
private fun TextViewerPreview(
    searchQuery: String,
    useRegex: Boolean,
    matches: List<TextViewerUiState.Match>,
    currentMatchIndex: Int,
) {
    FolderViewerTheme(dynamicColor = false, darkTheme = false) {
        TextViewerScreen(
            uiState = TextViewerUiState(
                title = "Main.kt",
                body = TextViewerUiState.Body.Text(PreviewText.trimIndent()),
                searchQuery = searchQuery,
                matchCase = false,
                useRegex = useRegex,
                patternInvalid = false,
                matches = matches,
                currentMatchIndex = currentMatchIndex,
                focusToken = 0,
                encodingMenu = TextViewerUiState.EncodingMenu(
                    currentLabel = "UTF-8",
                    choices = listOf(
                        TextViewerUiState.EncodingMenu.Choice(label = "UTF-8", selected = true),
                        TextViewerUiState.EncodingMenu.Choice(label = "Shift_JIS", selected = false),
                    ),
                ),
                callbacks = object : TextViewerUiState.Callbacks {
                    override fun onBack() = Unit
                    override fun onSearchQueryChange(query: String) = Unit
                    override fun onMatchCaseChange(matchCase: Boolean) = Unit
                    override fun onUseRegexChange(useRegex: Boolean) = Unit
                    override fun onNextMatch() = Unit
                    override fun onPreviousMatch() = Unit
                    override fun onEncodingSelected(label: String) = Unit
                },
            ),
        )
    }
}
