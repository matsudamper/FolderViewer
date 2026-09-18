package net.matsudamper.folderviewer.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

@Composable
internal fun LinkedMessageText(
    message: String,
    linkText: String?,
    onLinkClick: () -> Unit,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val linkSpanStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val annotatedMessage = remember(message, linkText, linkSpanStyle, onLinkClick) {
        buildLinkedMessage(
            message = message,
            linkText = linkText,
            linkSpanStyle = linkSpanStyle,
            onLinkClick = onLinkClick,
        )
    }
    Text(
        modifier = modifier,
        text = annotatedMessage,
        style = textStyle,
        color = textColor,
    )
}

private fun buildLinkedMessage(
    message: String,
    linkText: String?,
    linkSpanStyle: SpanStyle,
    onLinkClick: () -> Unit,
): AnnotatedString {
    val linkStartIndex = if (linkText.isNullOrEmpty()) -1 else message.lastIndexOf(linkText)
    if (linkText == null || linkStartIndex < 0) {
        return AnnotatedString(message)
    }
    return buildAnnotatedString {
        append(message.substring(0, linkStartIndex))
        withLink(
            LinkAnnotation.Clickable(
                tag = linkText,
                styles = TextLinkStyles(style = linkSpanStyle),
                linkInteractionListener = { onLinkClick() },
            ),
        ) {
            append(linkText)
        }
        append(message.substring(linkStartIndex + linkText.length))
    }
}
