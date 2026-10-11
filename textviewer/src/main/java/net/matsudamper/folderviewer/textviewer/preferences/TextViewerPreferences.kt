package net.matsudamper.folderviewer.textviewer.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class TextViewerPreferences(
    context: Context,
) {
    private val appContext = context.applicationContext

    val showLineNumbers: Flow<Boolean> = appContext.textViewerDataStore.data.map { preferences ->
        preferences[ShowLineNumbersKey] ?: false
    }

    val wrapLines: Flow<Boolean> = appContext.textViewerDataStore.data.map { preferences ->
        preferences[WrapLinesKey] ?: true
    }

    suspend fun setShowLineNumbers(show: Boolean) {
        appContext.textViewerDataStore.edit { preferences ->
            preferences[ShowLineNumbersKey] = show
        }
    }

    suspend fun setWrapLines(wrap: Boolean) {
        appContext.textViewerDataStore.edit { preferences ->
            preferences[WrapLinesKey] = wrap
        }
    }

    private companion object {
        val Context.textViewerDataStore by preferencesDataStore(name = "text_viewer")
        val ShowLineNumbersKey = booleanPreferencesKey("show_line_numbers")
        val WrapLinesKey = booleanPreferencesKey("wrap_lines")
    }
}
