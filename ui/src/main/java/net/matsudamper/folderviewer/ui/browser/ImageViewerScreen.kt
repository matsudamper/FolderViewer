package net.matsudamper.folderviewer.ui.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.matsudamper.folderviewer.repository.FileItem
import net.matsudamper.folderviewer.repository.FileRepository
import net.matsudamper.folderviewer.ui.R

@Composable
fun ImageViewerScreen(
    fileRepository: FileRepository?,
    path: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember(fileRepository) {
        ImageLoader.Builder(context)
            .components {
                if (fileRepository != null) {
                    add(FileRepositoryImageFetcher.Factory(fileRepository))
                }
            }
            .build()
    }

    // pathからファイル名を抽出（簡易的）
    val fileName = path.substringAfterLast('/').substringAfterLast('\\')
    val dummyFileItem = FileItem(
        name = fileName,
        path = path,
        isDirectory = false,
        size = 0,
        lastModified = 0,
    )

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

                val painter = rememberAsyncImagePainter(
                    model = dummyFileItem,
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
