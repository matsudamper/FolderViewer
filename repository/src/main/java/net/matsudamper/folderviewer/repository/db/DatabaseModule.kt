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
        ).build()
    }

    @Provides
    fun provideUploadJobDao(database: AppDatabase): UploadJobDao {
        return database.uploadJobDao()
    }
}
