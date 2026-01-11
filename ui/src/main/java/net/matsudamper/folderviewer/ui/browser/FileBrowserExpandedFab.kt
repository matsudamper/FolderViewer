package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.matsudamper.folderviewer.ui.R

@Composable
internal fun FileBrowserExpandedFab(
    modifier: Modifier = Modifier,
    onUploadFolderClick: () -> Unit,
    onUploadFileClick: () -> Unit,
    onFolderBrowserClick: () -> Unit,
    expandedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("フォルダビューアを開く")
            SmallFloatingActionButton(
                onClick = {
                    expandedChange(false)
                    onFolderBrowserClick()
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder_eye),
                    contentDescription = "フォルダビューアを開く",
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ファイルをアップロード")
            SmallFloatingActionButton(
                onClick = {
                    expandedChange(false)
                    onUploadFileClick()
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_upload_file),
                    contentDescription = "ファイルをアップロード",
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("フォルダをアップロード")
            SmallFloatingActionButton(
                onClick = {
                    expandedChange(false)
                    onUploadFolderClick()
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_upload_file),
                    contentDescription = "フォルダをアップロード",
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun PreviewFileBrowserExpandedFab() {
    FileBrowserExpandedFab(
        onUploadFolderClick = {},
        onUploadFileClick = {},
        onFolderBrowserClick = {},
        expandedChange = {},
    )
}
