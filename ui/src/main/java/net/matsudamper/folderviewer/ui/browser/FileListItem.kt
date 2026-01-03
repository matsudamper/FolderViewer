package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.util.formatBytes

@Composable
internal fun FileListItem(
    file: FileBrowserUiState.UiFileItem,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = file.callbacks::onClick),
        leadingContent = {
            FileIcon(
                file = file,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(56.dp)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small),
            )
        },
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            if (!file.isDirectory) {
                Text(formatBytes(file.size))
            }
        },
    )
}

@Composable
internal fun FileSmallListItem(
    file: FileBrowserUiState.UiFileItem,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = file.callbacks::onClick),
        leadingContent = {
            FileIcon(
                file = file,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(40.dp)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        },
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
internal fun FileGridItem(
    file: FileBrowserUiState.UiFileItem,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = file.callbacks::onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileIcon(
            file = file,
            imageLoader = imageLoader,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FileIcon(
    file: FileBrowserUiState.UiFileItem,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val imageSource = file.thumbnail
        if (imageSource != null) {
            AsyncImage(
                model = imageSource,
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_file),
                error = painterResource(R.drawable.ic_file),
            )
        } else {
            Icon(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                painter = painterResource(
                    id = if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file,
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
