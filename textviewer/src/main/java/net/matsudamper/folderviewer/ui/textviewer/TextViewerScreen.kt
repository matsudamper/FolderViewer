package net.matsudamper.folderviewer.ui.textviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import net.matsudamper.folderviewer.textviewer.R
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
                    LineEndingLabel(label = uiState.lineEndingLabel)
                    TextViewerMenu(
                        showLineNumbers = uiState.showLineNumbers,
                        wrapLines = uiState.wrapLines,
                        onShowLineNumbersChange = uiState.callbacks::onShowLineNumbersChange,
                        onWrapLinesChange = uiState.callbacks::onWrapLinesChange,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TextViewerBody(
                uiState = uiState,
                modifier = Modifier.fillMaxSize(),
            )
            TextViewerFindHost(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-2).dp),
            )
        }
    }
}

@Composable
private fun LineEndingLabel(label: String?) {
    if (label != null) {
        val description = stringResource(R.string.text_viewer_line_ending)
        Text(
            text = label,
            modifier = Modifier
                .padding(end = 12.dp)
                .semantics { contentDescription = "$description $label" },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TextViewerMenu(
    showLineNumbers: Boolean,
    wrapLines: Boolean,
    onShowLineNumbersChange: (Boolean) -> Unit,
    onWrapLinesChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.text_viewer_menu),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TextViewerCheckMenuItem(
                label = stringResource(R.string.text_viewer_show_line_numbers),
                checked = showLineNumbers,
                onClick = { onShowLineNumbersChange(!showLineNumbers) },
            )
            TextViewerCheckMenuItem(
                label = stringResource(R.string.text_viewer_wrap_lines),
                checked = wrapLines,
                onClick = { onWrapLinesChange(!wrapLines) },
            )
        }
    }
}

@Composable
private fun TextViewerCheckMenuItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                )
            }
        },
    )
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
    val horizontalScrollState = rememberScrollState()
    val scale = remember { mutableFloatStateOf(1f) }
    val scrollTarget = remember { mutableStateOf<Offset?>(null) }
    val display = remember(text) { TextViewerDisplayText.from(text) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val topPadding = 8.dp
    val sidePadding = 12.dp
    val gutterPadding = 8.dp
    val topPaddingPx = with(LocalDensity.current) { topPadding.toPx() }
    val matchBackground = MaterialTheme.colorScheme.secondaryContainer
    val matchContent = MaterialTheme.colorScheme.onSecondaryContainer
    val currentBackground = MaterialTheme.colorScheme.primary
    val currentContent = MaterialTheme.colorScheme.onPrimary
    val annotatedText = remember(
        display,
        uiState.matches,
        uiState.currentMatchIndex,
        matchBackground,
        matchContent,
        currentBackground,
        currentContent,
    ) {
        buildAnnotatedString {
            append(display.text)
            uiState.matches.forEachIndexed { index, match ->
                val style = if (index == uiState.currentMatchIndex) {
                    SpanStyle(background = currentBackground, color = currentContent)
                } else {
                    SpanStyle(background = matchBackground, color = matchContent)
                }
                val start = display.toDisplayOffset(match.start)
                val end = display.toDisplayOffset(match.endExclusive)
                if (start in 0 until display.text.length && end in (start + 1)..display.text.length) {
                    addStyle(style, start, end)
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
    LaunchedEffect(Unit) {
        snapshotFlow {
            ZoomScrollSnapshot(
                target = scrollTarget.value,
                scale = scale.floatValue,
                horizontalMax = horizontalScrollState.maxValue,
                verticalMax = verticalScrollState.maxValue,
            )
        }.collect { snapshot ->
            val target = snapshot.target ?: return@collect
            horizontalScrollState.scrollTo(
                target.x.roundToInt().coerceIn(0, snapshot.horizontalMax),
            )
            verticalScrollState.scrollTo(
                target.y.roundToInt().coerceIn(0, snapshot.verticalMax),
            )
        }
    }

    LaunchedEffect(uiState.focusToken, text) {
        if (uiState.focusToken == 0) return@LaunchedEffect
        val match = uiState.matches.getOrNull(uiState.currentMatchIndex) ?: return@LaunchedEffect
        val ready = snapshotFlow { textLayoutResult to verticalScrollState.viewportSize }
            .first { (layout, viewport) ->
                layout != null && viewport > 0 && layout.layoutInput.text.text == display.text
            }
        val layout = ready.first ?: return@LaunchedEffect
        val viewport = ready.second
        val displayStart = display.toDisplayOffset(match.start)
        if (displayStart !in 0 until layout.layoutInput.text.length) return@LaunchedEffect
        val box = layout.getBoundingBox(displayStart)
        val visibleScale = scale.floatValue
        val matchTop = (topPaddingPx + box.top) * visibleScale
        val matchBottom = (topPaddingPx + box.bottom) * visibleScale
        val visibleTop = verticalScrollState.value.toFloat()
        val visibleBottom = visibleTop + viewport
        if (matchTop >= visibleTop && matchBottom <= visibleBottom) return@LaunchedEffect
        val target = (matchTop - viewport * 0.25f).roundToInt().coerceAtLeast(0)
        verticalScrollState.animateScrollTo(target)
    }

    BoxWithConstraints(modifier = modifier) {
        val documentWidth = if (uiState.wrapLines) maxWidth else null
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .pinchToZoom(
                    scale = scale,
                    scrollTarget = scrollTarget,
                    verticalScroll = verticalScrollState,
                    horizontalScroll = horizontalScrollState,
                ),
        ) {
            TextViewerDocument(
                annotatedText = annotatedText,
                display = display,
                textStyle = textStyle,
                showLineNumbers = uiState.showLineNumbers,
                wrapLines = uiState.wrapLines,
                documentWidth = documentWidth,
                topPadding = topPadding,
                sidePadding = sidePadding,
                gutterPadding = gutterPadding,
                gutterColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier.gestureScale(scale.floatValue),
            )
        }
    }
}

@Composable
private fun TextViewerDocument(
    annotatedText: AnnotatedString,
    display: TextViewerDisplayText,
    textStyle: TextStyle,
    showLineNumbers: Boolean,
    wrapLines: Boolean,
    documentWidth: Dp?,
    topPadding: Dp,
    sidePadding: Dp,
    gutterPadding: Dp,
    gutterColor: Color,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val widthModifier = if (documentWidth == null) Modifier else Modifier.width(documentWidth)
    if (showLineNumbers && wrapLines) {
        WrappedLineNumberText(
            text = annotatedText,
            display = display,
            textStyle = textStyle,
            topPadding = topPadding,
            endPadding = sidePadding,
            gutterPadding = gutterPadding,
            gutterColor = gutterColor,
            onTextLayout = onTextLayout,
            modifier = modifier.then(widthModifier),
        )
    } else {
        Row(modifier = modifier.then(widthModifier)) {
            if (showLineNumbers) {
                Text(
                    text = display.lineNumberLabels(),
                    style = textStyle.copy(
                        color = gutterColor,
                        textAlign = TextAlign.End,
                    ),
                    softWrap = false,
                    modifier = Modifier.padding(
                        start = gutterPadding,
                        top = topPadding,
                        end = gutterPadding,
                        bottom = topPadding,
                    ),
                )
            }
            val bodyModifier = if (wrapLines) Modifier.fillMaxWidth() else Modifier
            SelectionContainer(modifier = bodyModifier) {
                Text(
                    text = annotatedText,
                    style = textStyle,
                    softWrap = wrapLines,
                    modifier = bodyModifier.padding(
                        start = if (showLineNumbers) 0.dp else sidePadding,
                        end = sidePadding,
                        top = topPadding,
                        bottom = topPadding,
                    ),
                    onTextLayout = onTextLayout,
                )
            }
        }
    }
}

@Composable
private fun WrappedLineNumberText(
    text: AnnotatedString,
    display: TextViewerDisplayText,
    textStyle: TextStyle,
    topPadding: Dp,
    endPadding: Dp,
    gutterPadding: Dp,
    gutterColor: Color,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gutterStyle = textStyle.copy(
        color = gutterColor,
        textAlign = TextAlign.End,
    )
    SubcomposeLayout(modifier = modifier) { constraints ->
        val probe = subcompose("width") {
            Text(
                text = display.lineNumberLabels().substringBefore('\n'),
                style = gutterStyle,
                softWrap = false,
                modifier = Modifier.padding(
                    start = gutterPadding,
                    top = topPadding,
                    end = gutterPadding,
                    bottom = topPadding,
                ),
            )
        }.first().measure(
            Constraints(
                maxWidth = Constraints.Infinity,
                maxHeight = Constraints.Infinity,
            ),
        )
        val bodyWidth = (constraints.maxWidth - probe.width).coerceAtLeast(0)
        var layoutResult: TextLayoutResult? = null
        val body = subcompose("body") {
            SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = text,
                    style = textStyle,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = endPadding, top = topPadding, bottom = topPadding),
                    onTextLayout = { result ->
                        layoutResult = result
                        onTextLayout(result)
                    },
                )
            }
        }.first().measure(constraints.copy(minWidth = bodyWidth, maxWidth = bodyWidth))
        val counts = layoutResult?.let { result -> display.visualLineCounts(result::getLineForOffset) }
        val labels = if (counts == null) display.lineNumberLabels() else display.lineNumberLabels(counts)
        val gutter = subcompose("gutter") {
            Text(
                text = labels,
                style = gutterStyle,
                softWrap = false,
                modifier = Modifier.padding(
                    start = gutterPadding,
                    top = topPadding,
                    end = gutterPadding,
                    bottom = topPadding,
                ),
            )
        }.first().measure(
            Constraints(
                maxWidth = Constraints.Infinity,
                maxHeight = Constraints.Infinity,
            ),
        )
        val width = probe.width + body.width
        val height = maxOf(gutter.height, body.height)
        layout(width, height) {
            gutter.place(0, 0)
            body.place(probe.width, 0)
        }
    }
}

@Composable
private fun TextViewerFindHost(
    uiState: TextViewerUiState,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    var focusSearch by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            withFrameNanos { }
            focusRequester.requestFocus()
            focusSearch = false
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            TextViewerFindBar(
                uiState = uiState,
                focusRequester = focusRequester,
            )
        }
        FindSemicircleTab(
            expanded = expanded,
            onClick = {
                if (!expanded) focusSearch = true
                expanded = !expanded
            },
            modifier = Modifier.offset(y = if (expanded) (-1).dp else 0.dp),
        )
    }
}

@Composable
private fun FindSemicircleTab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.text_viewer_search)
    val iconRes = if (expanded) R.drawable.ic_close else R.drawable.ic_search
    Box(
        modifier = modifier
            .size(width = FindTabDiameter, height = FindTabRadius)
            .shadow(elevation = 3.dp, shape = DownSemicircleShape)
            .clip(DownSemicircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.TopCenter,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun TextViewerFindBar(
    uiState: TextViewerUiState,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val searchDescription = stringResource(R.string.text_viewer_search)
    val fieldBorder = if (uiState.patternInvalid) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val status = searchStatus(uiState)
    val statusFailed = uiState.patternInvalid || (uiState.searchQuery.isNotEmpty() && uiState.matches.isEmpty())
    val statusColor = if (statusFailed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val fieldShape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .height(FindBarHeight)
            .shadow(elevation = 3.dp, shape = FindStripShape)
            .clip(FindStripShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = uiState.searchQuery,
            onValueChange = uiState.callbacks::onSearchQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { uiState.callbacks.onNextMatch() }),
            modifier = Modifier
                .width(148.dp)
                .height(26.dp)
                .focusRequester(focusRequester)
                .semantics { contentDescription = searchDescription }
                .clip(fieldShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = fieldBorder, shape = fieldShape)
                .padding(horizontal = 8.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (uiState.searchQuery.isEmpty()) {
                        Text(
                            text = searchDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
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
        Text(
            text = status,
            modifier = Modifier.widthIn(max = 72.dp),
            color = statusColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
        FindStepButton(
            enabled = uiState.matches.isNotEmpty(),
            iconRes = R.drawable.ic_keyboard_arrow_up,
            contentDescription = stringResource(R.string.text_viewer_previous_match),
            onClick = uiState.callbacks::onPreviousMatch,
        )
        FindStepButton(
            enabled = uiState.matches.isNotEmpty(),
            iconRes = R.drawable.ic_keyboard_arrow_down,
            contentDescription = stringResource(R.string.text_viewer_next_match),
            onClick = uiState.callbacks::onNextMatch,
        )
    }
}

@Composable
private fun FindToggleButton(
    checked: Boolean,
    label: String,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val background = if (checked) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = Modifier
            .padding(start = 2.dp)
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(role = Role.Checkbox, onClick = { onCheckedChange(!checked) })
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FindStepButton(
    enabled: Boolean,
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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

private data class ZoomScrollSnapshot(
    val target: Offset?,
    val scale: Float,
    val horizontalMax: Int,
    val verticalMax: Int,
)

private val FindBarHeight = 34.dp
private val FindTabDiameter = 44.dp
private val FindTabRadius = 22.dp

private object DownSemicircleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply {
            arcTo(
                rect = Rect(left = 0f, top = -size.height, right = size.width, bottom = size.height),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true,
            )
            close()
        }
        return Outline.Generic(path)
    }
}

private object FindStripShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val chamfer = with(density) { 12.dp.toPx() }.coerceAtMost(size.height)
        val path = Path()
        if (layoutDirection == LayoutDirection.Ltr) {
            path.moveTo(0f, 0f)
            path.lineTo(size.width, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(chamfer, size.height)
        } else {
            path.moveTo(0f, 0f)
            path.lineTo(size.width, 0f)
            path.lineTo(size.width - chamfer, size.height)
            path.lineTo(0f, size.height)
        }
        path.close()
        return Outline.Generic(path)
    }
}
