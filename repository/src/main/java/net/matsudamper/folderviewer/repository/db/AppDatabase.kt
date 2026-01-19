package net.matsudamper.folderviewer.repository.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UploadJobEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadJobDao(): UploadJobDao
}
