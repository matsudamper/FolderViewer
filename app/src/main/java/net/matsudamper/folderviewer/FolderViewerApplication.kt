package net.matsudamper.folderviewer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import net.matsudamper.folderviewer.di.appKoinModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

@HiltAndroidApp
class FolderViewerApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FolderViewerApplication)
            modules(appKoinModule)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
