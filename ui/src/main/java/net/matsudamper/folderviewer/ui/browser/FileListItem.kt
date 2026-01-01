package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.ui.R

@Composable
internal fun FileListItem(
    file: FileItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isImage = remember(file.name) {
        val name = file.name.lowercase()
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
            name.endsWith(".bmp") || name.endsWith(".gif") || name.endsWith(".webp")
    }

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            if (isImage) {
                AsyncImage(
                    model = FileImageSource.Thumbnail(file),
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_file),
                    error = painterResource(R.drawable.ic_file),
                )
            } else {
                Icon(
                    painter = painterResource(
                        id = if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file,
                    ),
                    contentDescription = null,
                )
            }
        },
        headlineContent = { Text(file.name) },
        supportingContent = {
            if (!file.isDirectory) {
                Text("${file.size} bytes")
            }
        },
    )
}
