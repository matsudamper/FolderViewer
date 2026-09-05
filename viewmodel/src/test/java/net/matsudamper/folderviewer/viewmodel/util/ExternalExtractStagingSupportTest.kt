package net.matsudamper.folderviewer.viewmodel.util

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ExternalExtractStagingSupportTest {
    private val cacheDir = File("/data/user/0/net.matsudamper.folderviewer/cache")

    @Test
    fun isStagedSource_detectsExternalExtractCacheFile() {
        assertTrue(
            ExternalExtractStagingSupport.isStagedSource(
                "/data/user/0/net.matsudamper.folderviewer/cache/external-extract-source/source-12345.tmp",
                cacheDir,
            ),
        )
    }

    @Test
    fun isStagedSource_rejectsRegularSourcePath() {
        assertFalse(
            ExternalExtractStagingSupport.isStagedSource(
                "/storage/emulated/0/Documents/FolderViewer/archive.zip",
                cacheDir,
            ),
        )
    }

    @Test
    fun isStagedSource_rejectsExternalStoragePathWithStagingDirectoryName() {
        assertFalse(
            ExternalExtractStagingSupport.isStagedSource(
                "/storage/emulated/0/external-extract-source/source-backup.zip",
                cacheDir,
            ),
        )
    }
}
