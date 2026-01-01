package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.matsudamper.folderviewer.coil.FileImageSource

@Composable
public fun ImageViewerScreen(
    imageLoader: ImageLoader,
    path: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // pathからファイル名を抽出（簡易的に）
    val fileName = remember(path) {
        path.substringAfterLast('/').substringAfterLast('\\')
    }

    val zoomState = rememberZoomState()

    Scaffold(
        topBar = {
            FileBrowserTopBar(
                currentPath = fileName,
                onBack = onBack,
                onUpClick = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .zoomable(zoomState),
            contentAlignment = Alignment.Center,
        ) {
            var isLoading by remember { mutableStateOf(true) }
            var isError by remember { mutableStateOf(false) }

            val imageRequest = remember(path, context) {
                ImageRequest.Builder(context)
                    .data(FileImageSource.Original(path))
                    .size(Size.ORIGINAL)
                    .build()
            }

            val painter = rememberAsyncImagePainter(
                model = imageRequest,
                imageLoader = imageLoader,
                onState = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                    isError = state is AsyncImagePainter.State.Error
                },
            )

            Image(
                painter = painter,
                contentDescription = fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            if (isLoading) {
                CircularProgressIndicator()
            }

            if (isError) {
                Text("Failed to load image")
            }
        }
    }
}
