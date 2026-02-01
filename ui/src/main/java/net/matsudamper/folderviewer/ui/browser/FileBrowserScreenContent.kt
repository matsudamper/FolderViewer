package net.matsudamper.folderviewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R

@Composable
internal fun FileBrowserScreenContent(
    uiState: FileBrowserUiState,
    snackbarHostState: SnackbarHostState,
    showCreateDirectoryDialog: Boolean = false,
    onCreateDirectoryDialogDismiss: () -> Unit = {},
    onConfirmCreateDirectory: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = uiState.isSelectionMode) {
        uiState.callbacks.onCancelSelection()
    }
    val callbacks = uiState.callbacks
    val containerColor = MaterialTheme.colorScheme.background
    var fabExpanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = {
            if (uiState.isSelectionMode) {
                FileBrowserSelectionTopBar(
                    selectedCount = uiState.selectedCount,
                    onCancelSelection = callbacks::onCancelSelection,
                )
            } else {
                FileBrowserTopBar(
                    title = uiState.title,
                    isFavorite = uiState.isFavorite,
                    visibleFavoriteButton = uiState.visibleFavoriteButton,
                    onBack = callbacks::onBack,
                    sortConfig = uiState.sortConfig,
                    onSortConfigChange = callbacks::onSortConfigChanged,
                    displayConfig = uiState.displayConfig,
                    onDisplayConfigChange = callbacks::onDisplayModeChanged,
                    onFavoriteClick = callbacks::onFavoriteClick,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.visibleFolderBrowserButton) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val animationDuration = 150
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn(animationSpec = tween(animationDuration))
                            .plus(
                                expandVertically(
                                    expandFrom = Alignment.Bottom,
                                    animationSpec = tween(animationDuration),
                                ),
                            ),
                        exit = fadeOut(animationSpec = tween(animationDuration))
                            .plus(
                                shrinkVertically(
                                    shrinkTowards = Alignment.Bottom,
                                    animationSpec = tween(animationDuration),
                                ),
                            ),
                    ) {
                        FileBrowserExpandedFab(
                            onUploadFolderClick = { callbacks.onUploadFolderClick() },
                            onUploadFileClick = { callbacks.onUploadFileClick() },
                            onFolderBrowserClick = { callbacks.onFolderBrowserClick() },
                            onCreateDirectoryClick = { callbacks.onCreateDirectoryClick() },
                            expandedChange = { fabExpanded = it },
                        )
                    }
                    val rotation by animateFloatAsState(
                        targetValue = if (fabExpanded) 45f else 0f,
                        animationSpec = tween(150),
                        label = "fab_rotation",
                    )
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "メニュー",
                            modifier = Modifier.rotate(rotation),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        FileBrowserBody(
            modifier = Modifier
                .fillMaxSize(),
            uiState = uiState,
            onRefresh = callbacks::onRefresh,
            contentPadding = innerPadding,
        )
        if (fabExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (fabExpanded) containerColor.copy(alpha = 0.9f) else Color.Unspecified)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { fabExpanded = false },
                    ),
            )
        }
    }

    if (showCreateDirectoryDialog) {
        var createDirectoryInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                onCreateDirectoryDialogDismiss()
            },
            title = { Text("ディレクトリを作成") },
            text = {
                TextField(
                    value = createDirectoryInput,
                    onValueChange = { createDirectoryInput = it },
                    label = { Text("ディレクトリ名") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createDirectoryInput.isNotBlank()) {
                            onConfirmCreateDirectory(createDirectoryInput)
                        }
                    },
                    enabled = createDirectoryInput.isNotBlank(),
                ) {
                    Text("作成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onCreateDirectoryDialogDismiss()
                    },
                ) {
                    Text("キャンセル")
                }
            },
        )
    }
}
