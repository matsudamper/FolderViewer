package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    uiState: TextViewerUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    colors = MyTopAppBarDefaults.topAppBarColors(),
                    title = {
                        Text(
                            text = uiState.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = uiState.callbacks::onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        EncodingMenuButton(encodingMenu = uiState.encodingMenu, onSelected = uiState.callbacks::onEncodingSelected)
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextViewerFindBar(
                        uiState = uiState,
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        },
    ) { innerPadding ->
        TextViewerBody(
            uiState = uiState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun EncodingMenuButton(
    encodingMenu: TextViewerUiState.EncodingMenu?,
    onSelected: (String) -> Unit,
) {
    if (encodingMenu != null) {
        var expanded by remember { mutableStateOf(false) }
        val description = stringResource(R.string.text_viewer_encoding)
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.semantics { contentDescription = "$description ${encodingMenu.currentLabel}" },
            ) {
                Text(text = encodingMenu.currentLabel)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(description) },
                    onClick = {},
                    enabled = false,
                )
                encodingMenu.choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        onClick = {
                            expanded = false
                            onSelected(choice.label)
                        },
                        leadingIcon = {
                            if (choice.selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextViewerBody(
    uiState: TextViewerUiState,
    modifier: Modifier = Modifier,
) {
    when (val body = uiState.body) {
        TextViewerUiState.Body.Loading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is TextViewerUiState.Body.Failure -> {
            Box(
                modifier = modifier.padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = failureMessage(body.reason),
                    textAlign = TextAlign.Center,
                )
            }
        }

        is TextViewerUiState.Body.Text -> {
            TextViewerContent(
                text = body.text,
                uiState = uiState,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TextViewerContent(
    text: String,
    uiState: TextViewerUiState,
    modifier: Modifier = Modifier,
) {
    val verticalScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val topPadding = 8.dp
    val topPaddingPx = with(LocalDensity.current) { topPadding.toPx() }
    val matchBackground = MaterialTheme.colorScheme.secondaryContainer
    val matchContent = MaterialTheme.colorScheme.onSecondaryContainer
    val currentBackground = MaterialTheme.colorScheme.primary
    val currentContent = MaterialTheme.colorScheme.onPrimary
    val annotatedText = remember(
        text,
        uiState.matches,
        uiState.currentMatchIndex,
        matchBackground,
        matchContent,
        currentBackground,
        currentContent,
    ) {
        buildAnnotatedString {
            append(text)
            uiState.matches.forEachIndexed { index, match ->
                val style = if (index == uiState.currentMatchIndex) {
                    SpanStyle(background = currentBackground, color = currentContent)
                } else {
                    SpanStyle(background = matchBackground, color = matchContent)
                }
                if (match.start in 0 until text.length && match.endExclusive in (match.start + 1)..text.length) {
                    addStyle(style, match.start, match.endExclusive)
                }
            }
        }
    }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )

    LaunchedEffect(uiState.focusToken, text) {
        if (uiState.focusToken == 0) return@LaunchedEffect
        val match = uiState.matches.getOrNull(uiState.currentMatchIndex) ?: return@LaunchedEffect
        val ready = snapshotFlow { textLayoutResult to verticalScrollState.viewportSize }
            .first { (layout, viewport) ->
                layout != null && viewport > 0 && layout.layoutInput.text.text == text
            }
        val layout = ready.first ?: return@LaunchedEffect
        val viewport = ready.second
        if (match.start !in 0 until layout.layoutInput.text.length) return@LaunchedEffect
        val box = layout.getBoundingBox(match.start)
        val matchTop = topPaddingPx + box.top
        val matchBottom = topPaddingPx + box.bottom
        val visibleTop = verticalScrollState.value.toFloat()
        val visibleBottom = visibleTop + viewport
        if (matchTop >= visibleTop && matchBottom <= visibleBottom) return@LaunchedEffect
        val target = (matchTop - viewport * 0.25f).roundToInt().coerceAtLeast(0)
        verticalScrollState.animateScrollTo(target)
    }

    SelectionContainer(
        modifier = modifier.verticalScroll(verticalScrollState),
    ) {
        Text(
            text = annotatedText,
            style = textStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = topPadding),
            onTextLayout = { textLayoutResult = it },
        )
    }
}

@Composable
private fun TextViewerFindBar(
    uiState: TextViewerUiState,
    modifier: Modifier = Modifier,
) {
    val searchDescription = stringResource(R.string.text_viewer_search)
    val borderColor = if (uiState.patternInvalid) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    val status = searchStatus(uiState)
    val statusColor = if (uiState.patternInvalid || (uiState.searchQuery.isNotEmpty() && uiState.matches.isEmpty())) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 2.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = uiState.searchQuery,
                    onValueChange = uiState.callbacks::onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { uiState.callbacks.onNextMatch() }),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = searchDescription }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = searchDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                FindToggleButton(
                    checked = uiState.matchCase,
                    label = "Aa",
                    contentDescription = stringResource(R.string.text_viewer_match_case),
                    onCheckedChange = uiState.callbacks::onMatchCaseChange,
                )
                FindToggleButton(
                    checked = uiState.useRegex,
                    label = ".*",
                    contentDescription = stringResource(R.string.text_viewer_use_regex),
                    onCheckedChange = uiState.callbacks::onUseRegexChange,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status,
                modifier = Modifier.weight(1f),
                color = statusColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = uiState.callbacks::onPreviousMatch,
                enabled = uiState.matches.isNotEmpty(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_up),
                    contentDescription = stringResource(R.string.text_viewer_previous_match),
                )
            }
            IconButton(
                onClick = uiState.callbacks::onNextMatch,
                enabled = uiState.matches.isNotEmpty(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = stringResource(R.string.text_viewer_next_match),
                )
            }
        }
    }
}

@Composable
private fun FindToggleButton(
    checked: Boolean,
    label: String,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun failureMessage(reason: TextViewerUiState.Reason): String {
    return when (reason) {
        TextViewerUiState.Reason.TooLarge -> stringResource(R.string.text_viewer_too_large)
        TextViewerUiState.Reason.Binary -> stringResource(R.string.text_viewer_binary)
        TextViewerUiState.Reason.Unreadable -> stringResource(R.string.text_viewer_unreadable)
    }
}

@Composable
private fun searchStatus(uiState: TextViewerUiState): String {
    return when {
        uiState.patternInvalid -> stringResource(R.string.text_viewer_invalid_regex)

        uiState.searchQuery.isEmpty() -> ""

        uiState.matches.isEmpty() -> stringResource(R.string.text_viewer_no_matches)

        else -> stringResource(
            R.string.text_viewer_match_position,
            uiState.currentMatchIndex + 1,
            uiState.matches.size,
        )
    }
}
