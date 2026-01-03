package net.matsudamper.folderviewer.repository

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.matsudamper.folderviewer.repository.proto.BrowserPreferencesProto
import net.matsudamper.folderviewer.repository.proto.SortConfigProto
import java.io.InputStream
import java.io.OutputStream

private const val DataStorageFileName = "browser_preferences.pb"

private val Context.browserPreferencesDataStore: DataStore<BrowserPreferencesProto> by dataStore(
    fileName = DataStorageFileName,
    serializer = BrowserPreferencesSerializer,
)

@Singleton
class PreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val folderBrowserSortConfig: Flow<FileSortConfig> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.folderBrowserSort.toDomain()
        }

    val fileBrowserSortConfig: Flow<FileSortConfig> = context.browserPreferencesDataStore.data
        .map { proto ->
            proto.fileBrowserSort.toDomain()
        }

    suspend fun saveFolderBrowserSortConfig(config: FileSortConfig) {
        context.browserPreferencesDataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFolderBrowserSort(config.toProto())
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

    data class FileSortConfig(
        val key: FileSortKey,
        val isAscending: Boolean,
    )

    enum class FileSortKey {
        Name,
        Date,
        Size,
    }
}

internal object BrowserPreferencesSerializer : Serializer<BrowserPreferencesProto> {
    override val defaultValue: BrowserPreferencesProto = BrowserPreferencesProto.newBuilder()
        .setFolderBrowserSort(
            SortConfigProto.newBuilder()
                .setKey(SortConfigProto.SortKey.NAME)
                .setIsAscending(true)
                .build(),
        )
        .setFileBrowserSort(
            SortConfigProto.newBuilder()
                .setKey(SortConfigProto.SortKey.NAME)
                .setIsAscending(true)
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
