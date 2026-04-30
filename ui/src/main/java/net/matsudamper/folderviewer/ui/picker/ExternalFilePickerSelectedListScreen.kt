package net.matsudamper.folderviewer.ui.picker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@Composable
fun ExternalFilePickerSelectedListScreen(
    uiState: ExternalFilePickerSelectedListUiState,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        uiState.callbacks.onBack()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SelectedListTopBar(onBack = uiState.callbacks::onBack)
        },
    ) { innerPadding ->
        if (uiState.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.no_files))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                items(
                    items = uiState.items,
                    key = { "${it.fileId.storageId.id}/${it.fileId.id}" },
                ) { item ->
                    SelectedFileItem(item = item)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectedListTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = { Text(text = stringResource(R.string.external_picker_selected_list_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectedFileItem(
    item: ExternalFilePickerSelectedListUiState.Item,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (item.isPreviewable) item.callbacks.onTap() },
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectedItemThumbnail(
            item = item,
            modifier = Modifier
                .size(48.dp)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.name,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = item.callbacks::onRemove) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = stringResource(R.string.external_picker_deselect),
            )
        }
    }
}

@Composable
private fun SelectedItemThumbnail(
    item: ExternalFilePickerSelectedListUiState.Item,
    modifier: Modifier = Modifier,
) {
    val thumbnail = item.thumbnail
    if (thumbnail != null) {
        SubcomposeAsyncImage(
            model = thumbnail,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_file),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> SubcomposeAsyncImageContent()
            }
        }
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_file),
            contentDescription = null,
            modifier = modifier,
        )
    }
}
