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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.matsudamper.folderviewer.coil.CoilImageLoaderFactory
import net.matsudamper.folderviewer.coil.FileImageSource
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository

@Composable
fun ImageViewerScreen(
    fileRepository: FileRepository?,
    path: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember(fileRepository) {
        CoilImageLoaderFactory.create(context, fileRepository)
    }

    // pathからファイル名を抽出（簡易的に）
    val fileName = remember(path) {
        path.substringAfterLast('/').substringAfterLast('\\')
    }
    val dummyFileItem = remember(path, fileName) {
        FileItem(
            name = fileName,
            path = path,
            isDirectory = false,
            size = 0,
            lastModified = 0,
        )
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
            if (fileRepository == null) {
                CircularProgressIndicator()
            } else {
                var isLoading by remember { mutableStateOf(true) }
                var isError by remember { mutableStateOf(false) }

                val imageRequest = remember(dummyFileItem, context) {
                    ImageRequest.Builder(context)
                        .data(FileImageSource.Original(dummyFileItem))
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
}
