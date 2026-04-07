package net.matsudamper.folderviewer.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OperationEntity::class,
        UploadOperationEntity::class,
        PasteOperationEntity::class,
        PasteFileEntity::class,
        DeleteFileEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun operationDao(): OperationDao
    abstract fun uploadOperationDao(): UploadOperationDao
    abstract fun pasteOperationDao(): PasteOperationDao
    abstract fun pasteFileDao(): PasteFileDao
    abstract fun deleteFileDao(): DeleteFileDao
}
