package net.matsudamper.folderviewer.coil

import coil.ImageLoader
import jakarta.inject.Inject

class CoilManager @Inject constructor(
    private val imageLoader: ImageLoader,
) {
    fun clearMemoryCache() {
        imageLoader.memoryCache?.clear()
    }

    @Suppress("OPT_IN_USAGE")
    fun clearDiskCache() {
        imageLoader.diskCache?.clear()
    }
}
