package net.matsudamper.folderviewer.repository

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.matsudamper.folderviewer.repository.proto.BrowserDisplayMode
import net.matsudamper.folderviewer.repository.proto.BrowserPreferencesProto
import net.matsudamper.folderviewer.repository.proto.FileBrowserDisplayConfig
import net.matsudamper.folderviewer.repository.proto.FolderBrowserDisplayConfig
import net.matsudamper.folderviewer.repository.proto.SortConfigProto

private const val DataStorageFileName = "browser_preferences.pb"

private val Context.browserPreferencesDataStore: DataStore<BrowserPreferencesProto> by dataStore(
    fileName = DataStorageFileName,
    serializer = BrowserPreferencesSerializer,
)

@Singleton
class PreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val folderBrowserFolderSortConfig: Flow<FileSortConfig> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.folderBrowserDisplayConfig.folderSort.toDomain()
        }

    val folderBrowserFileSortConfig: Flow<FileSortConfig> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.folderBrowserDisplayConfig.fileSort.toDomain()
        }

    val fileBrowserSortConfig: Flow<FileSortConfig> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.fileBrowserDisplayConfig.sortConfig.toDomain()
        }

    val folderBrowserDisplayMode: Flow<DisplayMode> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.folderBrowserDisplayConfig.displayMode.toDisplayMode()
        }

    val fileBrowserDisplayMode: Flow<DisplayMode> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.fileBrowserDisplayConfig.displayMode.toDisplayMode()
        }

    suspend fun saveFolderBrowserFolderSortConfig(config: FileSortConfig) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFolderBrowserDisplayConfig(
                    currentPrefs.folderBrowserDisplayConfig.toBuilder()
                        .setFolderSort(config.toProto())
                        .build(),
                )
                .build()
        }
    }

    suspend fun saveFolderBrowserFileSortConfig(config: FileSortConfig) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFolderBrowserDisplayConfig(
                    currentPrefs.folderBrowserDisplayConfig.toBuilder()
                        .setFileSort(config.toProto())
                        .build(),
                )
                .build()
        }
    }

    suspend fun saveFileBrowserSortConfig(config: FileSortConfig) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFileBrowserDisplayConfig(
                    currentPrefs.fileBrowserDisplayConfig.toBuilder()
                        .setSortConfig(config.toProto())
                        .build(),
                )
                .build()
        }
    }

    suspend fun saveFolderBrowserDisplayMode(mode: DisplayMode) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFolderBrowserDisplayConfig(
                    currentPrefs.folderBrowserDisplayConfig.toBuilder()
                        .setDisplayMode(mode.toProto())
                        .build(),
                )
                .build()
        }
    }

    suspend fun saveFileBrowserDisplayMode(mode: DisplayMode) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFileBrowserDisplayConfig(
                    currentPrefs.fileBrowserDisplayConfig.toBuilder()
                        .setDisplayMode(mode.toProto())
                        .build(),
                )
                .build()
        }
    }

    private fun SortConfigProto.toDomain(): FileSortConfig {
        return FileSortConfig(
            key = when (key) {
                SortConfigProto.SortKey.NAME -> FileSortKey.Name
                SortConfigProto.SortKey.DATE -> FileSortKey.Date
                SortConfigProto.SortKey.SIZE -> FileSortKey.Size
                SortConfigProto.SortKey.UNRECOGNIZED, null -> FileSortKey.Name
            },
            isAscending = isAscending,
        )
    }

    private fun FileSortConfig.toProto(): SortConfigProto {
        return SortConfigProto.newBuilder()
            .setKey(
                when (key) {
                    FileSortKey.Name -> SortConfigProto.SortKey.NAME
                    FileSortKey.Date -> SortConfigProto.SortKey.DATE
                    FileSortKey.Size -> SortConfigProto.SortKey.SIZE
                },
            )
            .setIsAscending(isAscending)
            .build()
    }

    private fun BrowserDisplayMode.toDisplayMode(): DisplayMode {
        return when (this) {
            BrowserDisplayMode.LIST -> DisplayMode.List
            BrowserDisplayMode.GRID -> DisplayMode.Grid
            BrowserDisplayMode.UNRECOGNIZED -> DisplayMode.List
        }
    }

    private fun DisplayMode.toProto(): BrowserDisplayMode {
        return when (this) {
            DisplayMode.List -> BrowserDisplayMode.LIST
            DisplayMode.Grid -> BrowserDisplayMode.GRID
        }
    }

    data class FileSortConfig(
        val key: FileSortKey,
        val isAscending: Boolean,
    )

    enum class FileSortKey {
        Name,
        Date,
        Size,
    }

    enum class DisplayMode {
        List,
        Grid,
    }
}

internal object BrowserPreferencesSerializer : Serializer<BrowserPreferencesProto> {
    override val defaultValue: BrowserPreferencesProto = BrowserPreferencesProto.newBuilder()
        .setFolderBrowserDisplayConfig(
            FolderBrowserDisplayConfig.newBuilder()
                .setFolderSort(
                    SortConfigProto.newBuilder()
                        .setKey(SortConfigProto.SortKey.NAME)
                        .setIsAscending(true)
                        .build(),
                )
                .setFileSort(
                    SortConfigProto.newBuilder()
                        .setKey(SortConfigProto.SortKey.NAME)
                        .setIsAscending(true)
                        .build(),
                )
                .setDisplayMode(BrowserDisplayMode.LIST)
                .build(),
        )
        .setFileBrowserDisplayConfig(
            FileBrowserDisplayConfig.newBuilder()
                .setSortConfig(
                    SortConfigProto.newBuilder()
                        .setKey(SortConfigProto.SortKey.NAME)
                        .setIsAscending(true)
                        .build(),
                )
                .setDisplayMode(BrowserDisplayMode.LIST)
                .build(),
        )
        .build()

    override suspend fun readFrom(input: InputStream): BrowserPreferencesProto {
        @Suppress("TooGenericExceptionCaught")
        try {
            return BrowserPreferencesProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        } catch (exception: Exception) {
            throw CorruptionException("Unexpected error reading proto.", exception)
        }
    }

    override suspend fun writeTo(t: BrowserPreferencesProto, output: OutputStream) {
        t.writeTo(output)
    }
}
