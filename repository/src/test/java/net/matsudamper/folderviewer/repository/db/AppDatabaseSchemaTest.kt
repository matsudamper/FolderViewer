package net.matsudamper.folderviewer.repository.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class AppDatabaseSchemaTest {
    @Test
    fun schemaChangeRequiresVersionBump() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val writableDatabase = database.openHelper.writableDatabase
            val identityHash = writableDatabase.query("SELECT identity_hash FROM room_master_table").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            assertEquals(
                "AppDatabaseのスキーマが変更されています。versionを上げてマイグレーションを追加し、" +
                    "このテストのExpectedVersionとExpectedIdentityHashを更新してください。",
                ExpectedIdentityHash,
                identityHash,
            )
            assertEquals(
                "AppDatabaseのversionがこのテストの期待値と一致していません。ExpectedVersionを更新してください。",
                ExpectedVersion,
                writableDatabase.version,
            )
        } finally {
            database.close()
        }
    }

    companion object {
        private const val ExpectedVersion = 10
        private const val ExpectedIdentityHash = "96e28e2fcc3d7bf0cced687081d494ee"
    }
}
