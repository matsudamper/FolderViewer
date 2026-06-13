package net.matsudamper.folderviewer.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OperationEntity::class,
        UploadOperationEntity::class,
        PasteOperationEntity::class,
        OperationFileEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun operationDao(): OperationDao
    abstract fun uploadOperationDao(): UploadOperationDao
    abstract fun pasteOperationDao(): PasteOperationDao
    abstract fun operationFileDao(): OperationFileDao
}
