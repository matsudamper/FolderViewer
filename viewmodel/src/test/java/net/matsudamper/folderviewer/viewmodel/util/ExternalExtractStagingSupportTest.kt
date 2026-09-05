package net.matsudamper.folderviewer.viewmodel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ExternalExtractStagingSupportTest {
    @Test
    fun isStagedSource_detectsExternalExtractCacheFile() {
        assertTrue(
            ExternalExtractStagingSupport.isStagedSource(
                "/data/cache/external-extract-source/source-12345.tmp",
            ),
        )
    }

    @Test
    fun isStagedSource_rejectsRegularSourcePath() {
        assertFalse(
            ExternalExtractStagingSupport.isStagedSource(
                "/storage/emulated/0/Documents/FolderViewer/archive.zip",
            ),
        )
    }
}
