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
    }
}

internal val Migration11To12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `paste_operations` ADD COLUMN `sourceDisplayPath` TEXT NOT NULL DEFAULT ''",
        )
    }
}

internal val Migration12To13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `extract_operations` (" +
                "`operationId` INTEGER NOT NULL, " +
                "`sourceFileObjectId` TEXT NOT NULL, " +
                "`sourceFileName` TEXT NOT NULL, " +
                "`outputName` TEXT NOT NULL, " +
                "`extractType` TEXT NOT NULL, " +
                "`parentFileObjectId` TEXT NOT NULL, " +
                "`parentDisplayPath` TEXT NOT NULL, " +
                "`localFolderPath` TEXT NOT NULL, " +
                "`openOnComplete` INTEGER NOT NULL, " +
                "`outputAbsolutePath` TEXT, " +
                "PRIMARY KEY(`operationId`), " +
                "FOREIGN KEY(`operationId`) REFERENCES `operations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_extract_operations_operationId` " +
                "ON `extract_operations` (`operationId`)",
        )
    }
}

internal val Migration13To14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `extract_operations` ADD COLUMN `openOnCompleteHandled` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

internal val Migration14To15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `extract_operations` ADD COLUMN `sourceAbsolutePath` TEXT",
        )
    }
}

internal val AppDatabaseMigrations = arrayOf(
    Migration9To10,
    Migration10To11,
    Migration11To12,
    Migration12To13,
    Migration13To14,
    Migration14To15,
)
