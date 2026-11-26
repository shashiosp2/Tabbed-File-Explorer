package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.SortOption
import com.example.engine.ViewLayout
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.explorerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tabbed_file_explorer_prefs"
)

data class ExplorerPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val showHiddenFiles: Boolean = true,
    val viewLayout: ViewLayout = ViewLayout.LIST,
    val sortOption: SortOption = SortOption.NAME,
    val sortAscending: Boolean = true,
    val foldersFirst: Boolean = true,
    val dualPaneEnabled: Boolean = false,
    val activeWorkspaceId: Long = 1L,
    val vaultPinHash: String = ""
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
        val VIEW_LAYOUT = stringPreferencesKey("view_layout")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val FOLDERS_FIRST = booleanPreferencesKey("folders_first")
        val DUAL_PANE = booleanPreferencesKey("dual_pane_enabled")
        val ACTIVE_WORKSPACE_ID = longPreferencesKey("active_workspace_id")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
    }

    val preferencesFlow: Flow<ExplorerPreferences> = context.explorerDataStore.data.map { prefs ->
        ExplorerPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: true,
            viewLayout = prefs[Keys.VIEW_LAYOUT]?.let {
                runCatching { ViewLayout.valueOf(it) }.getOrNull()
            } ?: ViewLayout.LIST,
            sortOption = prefs[Keys.SORT_OPTION]?.let {
                runCatching { SortOption.valueOf(it) }.getOrNull()
            } ?: SortOption.NAME,
            sortAscending = prefs[Keys.SORT_ASCENDING] ?: true,
            foldersFirst = prefs[Keys.FOLDERS_FIRST] ?: true,
            dualPaneEnabled = prefs[Keys.DUAL_PANE] ?: false,
            activeWorkspaceId = prefs[Keys.ACTIVE_WORKSPACE_ID] ?: 1L,
            vaultPinHash = prefs[Keys.VAULT_PIN_HASH] ?: ""
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.explorerDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.explorerDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.explorerDataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setViewLayout(layout: ViewLayout) {
        context.explorerDataStore.edit { it[Keys.VIEW_LAYOUT] = layout.name }
    }

    suspend fun setSort(option: SortOption, ascending: Boolean, foldersFirst: Boolean) {
        context.explorerDataStore.edit {
            it[Keys.SORT_OPTION] = option.name
            it[Keys.SORT_ASCENDING] = ascending
            it[Keys.FOLDERS_FIRST] = foldersFirst
        }
    }

    suspend fun setDualPaneEnabled(enabled: Boolean) {
        context.explorerDataStore.edit { it[Keys.DUAL_PANE] = enabled }
    }

    suspend fun setActiveWorkspaceId(id: Long) {
        context.explorerDataStore.edit { it[Keys.ACTIVE_WORKSPACE_ID] = id }
    }

    suspend fun setVaultPinHash(hash: String) {
        context.explorerDataStore.edit { it[Keys.VAULT_PIN_HASH] = hash }
    }
}
