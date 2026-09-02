package net.matsudamper.folderviewer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import net.matsudamper.folderviewer.viewmodel.browser.ExtractJobCompletionWatcher

@HiltAndroidApp
class FolderViewerApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var extractJobCompletionWatcher: ExtractJobCompletionWatcher

    override fun onCreate() {
        super.onCreate()
        extractJobCompletionWatcher.resume()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
