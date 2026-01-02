package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.util.formatBytes

@Composable
internal fun FileListItem(
    file: UiFileItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .aspectRatio(1f),
            ) {
                val imageSource = file.imageSource
                if (imageSource != null) {
                    AsyncImage(
                        model = imageSource,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.ic_file),
                        error = painterResource(R.drawable.ic_file),
                    )
                } else {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(
                            id = if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file,
                        ),
                        contentDescription = null,
                    )
                }
            }
        },
        headlineContent = { Text(file.name) },
        supportingContent = {
            if (!file.isDirectory) {
                Text(formatBytes(file.size))
            }
        },
    )
}
