package net.matsudamper.folderviewer.di

import android.content.Context
import javax.inject.Singleton
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.matsudamper.folderviewer.coil.CoilImageLoaderFactory
import net.matsudamper.folderviewer.repository.StorageRepository

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        storageRepository: StorageRepository,
    ): ImageLoader {
        return CoilImageLoaderFactory.create(context, storageRepository)
    }
}
