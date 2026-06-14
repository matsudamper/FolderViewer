package net.matsudamper.folderviewer.repository.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val Migration9To10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_operation_files_operationId_status` " +
                "ON `operation_files` (`operationId`, `status`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_operation_files_operationId_resolution` " +
                "ON `operation_files` (`operationId`, `resolution`)",
        )
    }
}

internal val Migration10To11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `operations` ADD COLUMN `pauseRequested` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE `paste_operations` ADD COLUMN `sourceDisplayPath` TEXT NOT NULL DEFAULT ''",
        )
    }
}

internal val AppDatabaseMigrations = arrayOf(Migration9To10, Migration10To11)
