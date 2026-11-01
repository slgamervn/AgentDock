package com.slg.agentdock.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.slg.agentdock.data.model.AppPreferences
import com.slg.agentdock.data.model.WorkspaceSnapshot
import com.slg.agentdock.data.model.WorkspaceTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.agentDockStore by preferencesDataStore(name = "agentdock_workspace")

class WorkspaceStore(context: Context) {
    private val store: DataStore<Preferences> = context.applicationContext.agentDockStore

    val snapshot: Flow<WorkspaceSnapshot> = store.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            val tabs = decodeTabs(preferences[TABS_KEY])
            val safeTabs = tabs.ifEmpty { listOf(WorkspaceTab(id = "workspace-default")) }
            val savedActiveId = preferences[ACTIVE_TAB_KEY]
            WorkspaceSnapshot(
                tabs = safeTabs,
                activeTabId = safeTabs.firstOrNull { it.id == savedActiveId }?.id ?: safeTabs.first().id,
                serverUrl = preferences[SERVER_URL_KEY].orEmpty(),
                closedTabs = decodeTabs(preferences[CLOSED_TABS_KEY]).take(MAX_CLOSED_TABS),
                preferences = AppPreferences(
                    themeMode = preferences[THEME_MODE_KEY].validatedTheme(),
                    accentColor = preferences[ACCENT_COLOR_KEY].validatedAccent(),
                    fontScale = (preferences[FONT_SCALE_KEY] ?: 1f).coerceIn(0.9f, 1.2f),
                    soundEnabled = preferences[SOUND_ENABLED_KEY] ?: false,
                    hapticsEnabled = preferences[HAPTICS_ENABLED_KEY] ?: true,
                ),
            )
        }

    suspend fun save(snapshot: WorkspaceSnapshot) {
        store.edit { preferences ->
            preferences[TABS_KEY] = encodeTabs(snapshot.tabs)
            preferences[CLOSED_TABS_KEY] = encodeTabs(snapshot.closedTabs.take(MAX_CLOSED_TABS))
            preferences[ACTIVE_TAB_KEY] = snapshot.activeTabId
            preferences[SERVER_URL_KEY] = snapshot.serverUrl
            preferences[THEME_MODE_KEY] = snapshot.preferences.themeMode.validatedTheme()
            preferences[ACCENT_COLOR_KEY] = snapshot.preferences.accentColor.validatedAccent()
            preferences[FONT_SCALE_KEY] = snapshot.preferences.fontScale.coerceIn(0.9f, 1.2f)
            preferences[SOUND_ENABLED_KEY] = snapshot.preferences.soundEnabled
            preferences[HAPTICS_ENABLED_KEY] = snapshot.preferences.hapticsEnabled
        }
    }

    private fun encodeTabs(tabs: List<WorkspaceTab>): String = JSONArray().apply {
        tabs.forEach { tab ->
            put(
                JSONObject()
                    .put("id", tab.id)
                    .put("title", tab.title)
                    .put("draft", tab.draft)
                    .put("pinned", tab.isPinned)
                    .put("updatedAt", tab.updatedAt)
                    .put("customTitle", tab.isTitleCustom),
            )
        }
    }.toString()

    private fun decodeTabs(raw: String?): List<WorkspaceTab> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    add(
                        WorkspaceTab(
                            id = id,
                            title = item.optString("title"),
                            draft = item.optString("draft"),
                            isPinned = item.optBoolean("pinned"),
                            updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                            isTitleCustom = item.optBoolean("customTitle"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_CLOSED_TABS = 10
        val TABS_KEY = stringPreferencesKey("workspace_tabs")
        val CLOSED_TABS_KEY = stringPreferencesKey("closed_workspace_tabs")
        val ACTIVE_TAB_KEY = stringPreferencesKey("active_tab_id")
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED_KEY = booleanPreferencesKey("haptics_enabled")
    }
}

private fun String?.validatedTheme(): String = when (this) {
    "dark", "light" -> this
    else -> "system"
}

private fun String?.validatedAccent(): String = when (this) {
    "blue", "amber" -> this
    else -> "emerald"
}
