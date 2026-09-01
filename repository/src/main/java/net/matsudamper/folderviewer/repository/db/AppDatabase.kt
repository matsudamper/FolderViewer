package net.matsudamper.folderviewer.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OperationEntity::class,
        UploadOperationEntity::class,
        PasteOperationEntity::class,
        ExtractOperationEntity::class,
        OperationFileEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun operationDao(): OperationDao
    abstract fun uploadOperationDao(): UploadOperationDao
    abstract fun pasteOperationDao(): PasteOperationDao
    abstract fun extractOperationDao(): ExtractOperationDao
    abstract fun operationFileDao(): OperationFileDao
}
