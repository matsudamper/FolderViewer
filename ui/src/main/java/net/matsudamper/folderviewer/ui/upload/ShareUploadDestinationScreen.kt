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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
}

@Composable
private fun FolderRow(
    folder: ShareUploadDestinationUiState.Folder,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { folder.callbacks.onClick() },
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
    PreviewContent(canUpload = true)
}

@Composable
@Preview
private fun PreviewCannotUpload() {
    PreviewContent(canUpload = false)
}

@Composable
private fun PreviewContent(canUpload: Boolean) {
    FolderViewerTheme {
        ShareUploadDestinationScreen(
            uiState = ShareUploadDestinationUiState(
                title = "サンプルストレージ",
                pendingCount = 3,
                canUpload = canUpload,
                isRefreshing = false,
                contentState = ShareUploadDestinationUiState.ContentState.Content(
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
                ),
                callbacks = object : ShareUploadDestinationUiState.Callbacks {
                    override fun onBack() = Unit
                    override fun onRefresh() = Unit
                    override fun onUploadHere() = Unit
                },
            ),
        )
    }
}
