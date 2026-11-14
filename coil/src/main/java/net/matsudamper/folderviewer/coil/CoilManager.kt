package net.matsudamper.folderviewer.coil

import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import jakarta.inject.Inject

class CoilManager @Inject constructor(
    private val imageLoader: ImageLoader,
) {
    fun clearMemoryCache() {
        imageLoader.memoryCache?.clear()
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearDiskCache() {
        imageLoader.diskCache?.clear()
    }
}
