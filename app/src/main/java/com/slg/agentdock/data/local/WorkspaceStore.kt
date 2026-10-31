package com.slg.agentdock.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
            )
        }

    suspend fun save(snapshot: WorkspaceSnapshot) {
        store.edit { preferences ->
            preferences[TABS_KEY] = encodeTabs(snapshot.tabs)
            preferences[ACTIVE_TAB_KEY] = snapshot.activeTabId
            preferences[SERVER_URL_KEY] = snapshot.serverUrl
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
                    .put("updatedAt", tab.updatedAt),
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
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val TABS_KEY = stringPreferencesKey("workspace_tabs")
        val ACTIVE_TAB_KEY = stringPreferencesKey("active_tab_id")
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
    }
}
