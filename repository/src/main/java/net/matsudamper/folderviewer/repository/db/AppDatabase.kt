package net.matsudamper.folderviewer.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UploadJobEntity::class, PasteJobEntity::class, PasteFileEntity::class],
    version = 6,
    exportSchema = false,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadJobDao(): UploadJobDao
    abstract fun pasteJobDao(): PasteJobDao
    abstract fun pasteFileDao(): PasteFileDao
}
