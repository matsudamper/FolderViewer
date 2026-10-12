package net.matsudamper.folderviewer.viewmodel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OutputSpaceGuardTest {
    @Test
    fun canWrite_checksFreeSpaceAtStartAndAfterInterval() {
        var checks = 0
        val guard = OutputSpaceGuard {
            checks++
            true
        }
        assertTrue(guard.canWrite(1))
        assertEquals(1, checks)

        assertTrue(guard.canWrite(ExtractStorageLimit.RECHECK_INTERVAL_BYTES - 1))
        assertEquals(1, checks)

        assertTrue(guard.canWrite(1))
        assertEquals(2, checks)
    }

    @Test
    fun canWrite_returnsFalseWhenFreeSpaceCheckFails() {
        val guard = OutputSpaceGuard { false }
        assertFalse(guard.canWrite(1))
    }
}
