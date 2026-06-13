package net.matsudamper.folderviewer.repository.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrateFromOldestSchemaToLatest() {
        helper.createDatabase(TestDbName, OldestSchemaVersion).use { db ->
            db.execSQL(
                "INSERT INTO operations (id, type, name, description, status, createdAt) " +
                    "VALUES (1, 'UPLOAD', 'name', 'description', 'RUNNING', 0)",
            )
            db.execSQL(
                "INSERT INTO operation_files " +
                    "(operationId, fileName, relativePath, isDirectory, status, transferredBytes, sourceDeleted) " +
                    "VALUES (1, 'file.txt', '', 0, 'PENDING', 0, 0)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TestDbName,
            latestSchemaVersion(),
            true,
            Migration9To10,
        )

        migrated.query("SELECT COUNT(*) FROM operation_files").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    private fun latestSchemaVersion(): Int {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.assets.list(AppDatabase::class.java.canonicalName.orEmpty())
            .orEmpty()
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .max()
    }

    companion object {
        private const val TestDbName = "migration-test"
        private const val OldestSchemaVersion = 9
    }
}
