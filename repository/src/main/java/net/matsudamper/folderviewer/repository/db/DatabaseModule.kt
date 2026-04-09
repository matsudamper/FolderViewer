package net.matsudamper.folderviewer.repository.db

import android.content.Context
import androidx.room.Room
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "folder_viewer_db",
        ).fallbackToDestructiveMigrationFrom(
            dropAllTables = true,
            startVersions = intArrayOf(3, 4, 5, 6, 7),
        ).build()
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
    fun providePasteFileDao(database: AppDatabase): PasteFileDao {
        return database.pasteFileDao()
    }

    @Provides
    fun provideDeleteFileDao(database: AppDatabase): DeleteFileDao {
        return database.deleteFileDao()
    }
}
