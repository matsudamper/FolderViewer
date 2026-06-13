package net.matsudamper.folderviewer.repository.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    private val migration9To10 = object : Migration(9, 10) {
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "folder_viewer_db",
        ).fallbackToDestructiveMigrationFrom(
            dropAllTables = true,
            startVersions = intArrayOf(3, 4, 5, 6, 7, 8),
        ).addMigrations(migration9To10)
            .build()
    }

    @Provides
    fun provideOperationDao(database: AppDatabase): OperationDao {
        return database.operationDao()
    }

    @Provides
    fun provideUploadOperationDao(database: AppDatabase): UploadOperationDao {
        return database.uploadOperationDao()
    }

    @Provides
    fun providePasteOperationDao(database: AppDatabase): PasteOperationDao {
        return database.pasteOperationDao()
    }

    @Provides
    fun provideOperationFileDao(database: AppDatabase): OperationFileDao {
        return database.operationFileDao()
    }
}
