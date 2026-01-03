package net.matsudamper.folderviewer.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.matsudamper.folderviewer.ui.R
import net.matsudamper.folderviewer.ui.theme.MyTopAppBarDefaults

@Composable
fun ImageViewerScreen(
    uiState: ImageViewerUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageSource = uiState.imageSource

    val zoomState = rememberZoomState()
    var showTopBar by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .zoomable(
                    zoomState = zoomState,
                    onTap = {
                        showTopBar = !showTopBar
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            var imageState: AsyncImagePainter.State by remember { mutableStateOf(AsyncImagePainter.State.Empty) }

            val imageRequest = remember(imageSource, context) {
                ImageRequest.Builder(context)
                    .data(imageSource)
                    .size(Size.ORIGINAL)
                    .build()
            }

            val painter = rememberAsyncImagePainter(
                model = imageRequest,
                onState = { state ->
                    imageState = state
                },
            )

            Image(
                painter = painter,
                contentDescription = uiState.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            when (val state = imageState) {
                is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator()
                }

                is AsyncImagePainter.State.Error -> {
                    LaunchedEffect(state) {
                        state.result.throwable.printStackTrace()
                    }
                    Column {
                        Text("Failed to load image")
                        Text(state.result.throwable.message ?: "No Error Message")
                    }
                }

                else -> Unit
            }
        }

        AnimatedVisibility(
            visible = showTopBar,
            enter = fadeIn() + slideInVertically { height -> -height },
            exit = fadeOut() + slideOutVertically { height -> -height },
        ) {
            ImageViewerTopBar(
                title = uiState.title,
                onBack = {
                    uiState.callbacks.onBack()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors = MyTopAppBarDefaults.topAppBarColors(),
        title = {
            Text(
                text = title,
                maxLines = 1,
            )
        },
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
