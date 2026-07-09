package net.matsudamper.folderviewer.ui.upload

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.FolderViewerTheme
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareUploadDestinationScreen(
    uiState: ShareUploadDestinationUiState,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        uiState.callbacks.onBack()
    }

    var showCreateDirectoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
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
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "戻る",
                        )
                    }
                },
                actions = {
                    if (uiState.canCreateDirectory) {
                        IconButton(onClick = { showCreateDirectoryDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_folder_add),
                                contentDescription = "ディレクトリを作成",
                            )
                        }
                    }
                },
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (uiState.canUpload) {
                ExtendedFloatingActionButton(
                    onClick = uiState.callbacks::onUploadHere,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_upload_file),
                            contentDescription = null,
                        )
                    },
                    text = { Text("ここにアップロード (${uiState.pendingCount})") },
                )
            }
        },
    ) { innerPadding ->
        when (val contentState = uiState.contentState) {
            ShareUploadDestinationUiState.ContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            ShareUploadDestinationUiState.ContentState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "読み込みに失敗しました")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = uiState.callbacks::onRefresh) {
                            Text(text = "再読み込み")
                        }
                    }
                }
            }

            ShareUploadDestinationUiState.ContentState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "フォルダがありません")
                }
            }

            is ShareUploadDestinationUiState.ContentState.Content -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    items(
                        items = contentState.folders,
                        key = { folder -> folder.key },
                    ) { folder ->
                        FolderRow(folder = folder)
                    }
                }
            }
        }
    }

    if (showCreateDirectoryDialog) {
        CreateDirectoryDialog(
            onDismiss = { showCreateDirectoryDialog = false },
            onConfirm = { name ->
                uiState.callbacks.onCreateDirectory(name)
                showCreateDirectoryDialog = false
            },
        )
    }
}

@Composable
private fun CreateDirectoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ディレクトリを作成") },
        text = {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("ディレクトリ名") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = input.isNotBlank(),
            ) {
                Text("作成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun FolderRow(
    folder: ShareUploadDestinationUiState.Folder,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { folder.callbacks.onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
            )
        },
        headlineContent = {
            Text(
                text = folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
@Preview
private fun Preview() {
    PreviewContent(contentState = previewContentState())
}

@Composable
@Preview
private fun PreviewCannotUpload() {
    PreviewContent(canUpload = false, contentState = previewContentState())
}

@Composable
@Preview
private fun PreviewLoading() {
    PreviewContent(contentState = ShareUploadDestinationUiState.ContentState.Loading)
}

@Composable
@Preview
private fun PreviewError() {
    PreviewContent(contentState = ShareUploadDestinationUiState.ContentState.Error)
}

@Composable
@Preview
private fun PreviewEmpty() {
    PreviewContent(contentState = ShareUploadDestinationUiState.ContentState.Empty)
}

private fun previewContentState(): ShareUploadDestinationUiState.ContentState {
    return ShareUploadDestinationUiState.ContentState.Content(
        folders = listOf(
            ShareUploadDestinationUiState.Folder(
                name = "ドキュメント",
                key = "doc",
                callbacks = object : ShareUploadDestinationUiState.Folder.Callbacks {
                    override fun onClick() = Unit
                },
            ),
            ShareUploadDestinationUiState.Folder(
                name = "画像",
                key = "images",
                callbacks = object : ShareUploadDestinationUiState.Folder.Callbacks {
                    override fun onClick() = Unit
                },
            ),
        ),
    )
}

@Composable
private fun PreviewContent(
    contentState: ShareUploadDestinationUiState.ContentState,
    canUpload: Boolean = true,
) {
    FolderViewerTheme {
        ShareUploadDestinationScreen(
            uiState = ShareUploadDestinationUiState(
                title = "サンプルストレージ",
                pendingCount = 3,
                canUpload = canUpload,
                canCreateDirectory = true,
                isRefreshing = false,
                contentState = contentState,
                callbacks = object : ShareUploadDestinationUiState.Callbacks {
                    override fun onBack() = Unit
                    override fun onRefresh() = Unit
                    override fun onUploadHere() = Unit
                    override fun onCreateDirectory(name: String) = Unit
                },
            ),
        )
    }
}
