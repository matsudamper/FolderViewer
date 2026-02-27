package net.matsudamper.folderviewer.di

import net.matsudamper.folderviewer.state.GlobalActionStateStore
import org.koin.dsl.module

internal val appKoinModule = module {
    single { GlobalActionStateStore() }
}
