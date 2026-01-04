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
import net.matsudamper.folderviewer.repository.proto.BrowserPreferencesProto
import net.matsudamper.folderviewer.repository.proto.DisplayConfigProto
import net.matsudamper.folderviewer.repository.proto.FolderBrowserSortConfigProto
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
            proto.folderBrowserSort.folderSort.toDomain()
        }

    val folderBrowserFileSortConfig: Flow<FileSortConfig> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.folderBrowserSort.fileSort.toDomain()
        }

    val fileBrowserSortConfig: Flow<FileSortConfig> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.fileBrowserSort.toDomain()
        }

    val folderBrowserDisplayMode: Flow<DisplayMode> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.folderBrowserDisplay.toDisplayMode()
        }

    val fileBrowserDisplayMode: Flow<DisplayMode> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.fileBrowserDisplay.toDisplayMode()
        }

    suspend fun saveFolderBrowserFolderSortConfig(config: FileSortConfig) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFolderBrowserSort(
                    currentPrefs.folderBrowserSort.toBuilder()
                        .setFolderSort(config.toProto())
                        .build(),
                )
                .build()
        }
    }

    suspend fun saveFolderBrowserFileSortConfig(config: FileSortConfig) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFolderBrowserSort(
                    currentPrefs.folderBrowserSort.toBuilder()
                        .setFileSort(config.toProto())
                        .build(),
                )
                .build()
        }
    }

    suspend fun saveFileBrowserSortConfig(config: FileSortConfig) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFileBrowserSort(config.toProto())
                .build()
        }
    }

    suspend fun saveFolderBrowserDisplayMode(mode: DisplayMode) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFolderBrowserDisplay(mode.toProto())
                .build()
        }
    }

    suspend fun saveFileBrowserDisplayMode(mode: DisplayMode) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFileBrowserDisplay(mode.toProto())
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

    private fun DisplayConfigProto.toDisplayMode(): DisplayMode {
        return when (mode) {
            DisplayConfigProto.DisplayMode.LIST -> DisplayMode.List
            DisplayConfigProto.DisplayMode.GRID -> DisplayMode.Grid
            DisplayConfigProto.DisplayMode.UNRECOGNIZED, null -> DisplayMode.List
        }
    }

    private fun DisplayMode.toProto(): DisplayConfigProto {
        return DisplayConfigProto.newBuilder()
            .setMode(
                when (this) {
                    DisplayMode.List -> DisplayConfigProto.DisplayMode.LIST
                    DisplayMode.Grid -> DisplayConfigProto.DisplayMode.GRID
                },
            )
            .build()
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
        .setFolderBrowserSort(
            FolderBrowserSortConfigProto.newBuilder()
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
                .build(),
        )
        .setFileBrowserSort(
            SortConfigProto.newBuilder()
                .setKey(SortConfigProto.SortKey.NAME)
                .setIsAscending(true)
                .build(),
        )
        .setFolderBrowserDisplay(
            DisplayConfigProto.newBuilder()
                .setMode(DisplayConfigProto.DisplayMode.LIST)
                .build(),
        )
        .setFileBrowserDisplay(
            DisplayConfigProto.newBuilder()
                .setMode(DisplayConfigProto.DisplayMode.LIST)
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
