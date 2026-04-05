package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.util.formatBytes

@Composable
internal fun FileHeaderItem(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileSmallListItem(
    file: FileBrowserUiState.UiFileItem.File,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = file.callbacks::onClick,
                onLongClick = file.callbacks::onLongClick,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = file.isSelected,
                onCheckedChange = file.callbacks::onCheckedChange,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        FileIcon(
            file = file,
            modifier = Modifier
                .size(24.dp)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(modifier = Modifier.width(8.dp))
        FileNameText(
            name = file.name,
            textOverflow = textOverflow,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileMediumListItem(
    file: FileBrowserUiState.UiFileItem.File,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = file.callbacks::onClick,
                onLongClick = file.callbacks::onLongClick,
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = file.isSelected,
                onCheckedChange = file.callbacks::onCheckedChange,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        FileIcon(
            file = file,
            modifier = Modifier
                .size(50.dp)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            FileNameText(
                name = file.name,
                maxLines = 2,
                textOverflow = textOverflow,
            )

            if (!file.isDirectory) {
                Text(
                    text = formatBytes(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileLargeListItem(
    file: FileBrowserUiState.UiFileItem.File,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = file.callbacks::onClick,
                onLongClick = file.callbacks::onLongClick,
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = file.isSelected,
                onCheckedChange = file.callbacks::onCheckedChange,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        FileIcon(
            file = file,
            modifier = Modifier
                .size(100.dp)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            FileNameText(
                name = file.name,
                maxLines = 4,
                textOverflow = textOverflow,
            )

            if (!file.isDirectory) {
                Text(
                    text = formatBytes(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FileNameText(
    name: String,
    textOverflow: TextOverflow,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        modifier = modifier,
        maxLines = maxLines,
        overflow = textOverflow,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
internal fun FileSmallGridItem(
    file: FileBrowserUiState.UiFileItem.File,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    FileGridItem(
        file = file,
        modifier = modifier,
        textOverflow = textOverflow,
        padding = 4.dp,
        isSelectionMode = isSelectionMode,
    )
}

@Composable
internal fun FileLargeGridItem(
    file: FileBrowserUiState.UiFileItem.File,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    FileGridItem(
        file = file,
        modifier = modifier,
        textOverflow = textOverflow,
        padding = 8.dp,
        isSelectionMode = isSelectionMode,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    file: FileBrowserUiState.UiFileItem.File,
    padding: Dp,
    textOverflow: TextOverflow,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = file.callbacks::onClick,
                onLongClick = file.callbacks::onLongClick,
            )
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            FileIcon(
                file = file,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isSelectionMode) {
                Checkbox(
                    checked = file.isSelected,
                    onCheckedChange = file.callbacks::onCheckedChange,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = textOverflow,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun FileIcon(
    file: FileBrowserUiState.UiFileItem.File,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val imageSource = file.thumbnail
        if (imageSource != null) {
            SubcomposeAsyncImage(
                model = imageSource,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            ) {
                when (val state = painter.state) {
                    is AsyncImagePainter.State.Loading,
                    is AsyncImagePainter.State.Error,
                    -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_file),
                            contentDescription = if (state is AsyncImagePainter.State.Loading) "loading" else "error",
                            modifier = Modifier.fillMaxSize(),
                            tint = LocalContentColor.current,
                        )
                    }

                    else -> {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
        } else {
            Icon(
                modifier = Modifier
                    .fillMaxSize(),
                painter = painterResource(
                    id = if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file,
                ),
                contentDescription = if (file.isDirectory) "folder" else "file",
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFileSmallListItem() {
    MaterialTheme {
        FileSmallListItem(
            file = previewUiFileItem(),
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFileMediumListItem() {
    MaterialTheme {
        FileMediumListItem(
            file = previewUiFileItem(),
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFileLargeListItem() {
    MaterialTheme {
        FileLargeListItem(
            file = previewUiFileItem(),
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFileSmallGridItem() {
    MaterialTheme {
        FileSmallGridItem(
            file = previewUiFileItem(),
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFileLargeGridItem() {
    MaterialTheme {
        FileLargeGridItem(
            file = previewUiFileItem(),
            textOverflow = TextOverflow.Ellipsis,
            isSelectionMode = false,
        )
    }
}

@Composable
private fun previewUiFileItem() = FileBrowserUiState.UiFileItem.File(
    name = "FileName.txt",
    key = "/path/to/FileName.txt",
    isDirectory = false,
    size = 1024L * 1024L,
    lastModified = 0L,
    thumbnail = null,
    isSelected = false,
    callbacks = object : FileBrowserUiState.UiFileItem.File.Callbacks {
        override fun onClick() = Unit
        override fun onLongClick() = Unit
        override fun onCheckedChange(checked: Boolean) = Unit
    },
)
