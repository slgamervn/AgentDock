package com.slg.agentdock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.slg.agentdock.data.model.AppPreferences
import com.slg.agentdock.data.model.WorkspaceSnapshot
import com.slg.agentdock.data.model.WorkspaceTab
import com.slg.agentdock.data.repository.WorkspaceRepository
import com.slg.agentdock.domain.validation.normalizeServerAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AgentDockUiState(
    val tabs: List<WorkspaceTab> = emptyList(),
    val activeTabId: String = "",
    val serverUrl: String = "",
    val closedTabs: List<WorkspaceTab> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val isLoaded: Boolean = false,
)

/** Small navigation-only data. Draft contents stay inside the active screen. */
data class WorkspaceTabSummary(
    val id: String,
    val title: String,
)

data class AgentDockChromeState(
    val tabs: List<WorkspaceTabSummary> = emptyList(),
    val activeTabId: String = "",
    val serverUrl: String = "",
    val preferences: AppPreferences = AppPreferences(),
    val isLoaded: Boolean = false,
)

sealed interface AgentDockEvent {
    data object DraftSaved : AgentDockEvent
    data object ServerAddressSaved : AgentDockEvent
    data object InvalidServerAddress : AgentDockEvent
}

class AgentDockViewModel(
    private val repository: WorkspaceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AgentDockUiState())
    val uiState = _uiState.asStateFlow()

    // Draft keystrokes update only the active screen. The app shell observes
    // this lightweight projection and is not invalidated by draft text changes.
    val chromeState = _uiState
        .map { state ->
            AgentDockChromeState(
                tabs = state.tabs.map { tab -> WorkspaceTabSummary(tab.id, tab.title) },
                activeTabId = state.activeTabId,
                serverUrl = state.serverUrl,
                preferences = state.preferences,
                isLoaded = state.isLoaded,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgentDockChromeState())

    val activeTab = _uiState
        .map { state ->
            state.tabs.firstOrNull { it.id == state.activeTabId } ?: state.tabs.firstOrNull()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = MutableSharedFlow<AgentDockEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = repository.snapshot.first()
            _uiState.update {
                it.copy(
                    tabs = saved.tabs,
                    activeTabId = saved.activeTabId,
                    serverUrl = saved.serverUrl,
                    closedTabs = saved.closedTabs,
                    preferences = saved.preferences,
                    isLoaded = true,
                )
            }
        }
    }

    fun addTab(): String {
        val tab = WorkspaceTab(id = UUID.randomUUID().toString())
        _uiState.update { state ->
            state.copy(tabs = state.tabs + tab, activeTabId = tab.id)
        }
        persistSoon()
        return tab.id
    }

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            if (state.tabs.none { it.id == tabId }) state
            else state.copy(activeTabId = tabId)
        }
        persistSoon()
    }

    fun updateDraft(tabId: String, value: String) {
        _uiState.update { state ->
            if (state.tabs.none { it.id == tabId }) return@update state
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(draft = value) else tab
                },
            )
        }
        persistSoon()
    }

    fun renameTab(tabId: String, value: String) {
        val safeTitle = value.trim().replace(Regex("\\s+"), " ").take(48)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(title = safeTitle, isTitleCustom = safeTitle.isNotBlank())
                    } else tab
                },
            )
        }
        persistSoon()
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val closing = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            val remaining = state.tabs.filterNot { it.id == tabId }
            val nextTabs = remaining.ifEmpty {
                listOf(WorkspaceTab(id = UUID.randomUUID().toString()))
            }
            val nextActiveId = if (state.activeTabId == tabId) nextTabs.first().id else state.activeTabId
            state.copy(
                tabs = nextTabs,
                activeTabId = nextActiveId,
                closedTabs = (listOf(closing) + state.closedTabs.filterNot { it.id == closing.id }).take(MAX_CLOSED_TABS),
            )
        }
        persistSoon()
    }

    fun restoreLastClosedTab() {
        _uiState.update { state ->
            val restored = state.closedTabs.firstOrNull() ?: return@update state
            state.copy(
                tabs = state.tabs + restored,
                activeTabId = restored.id,
                closedTabs = state.closedTabs.drop(1),
            )
        }
        persistSoon()
    }

    fun togglePinned(tabId: String) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(isPinned = !tab.isPinned) else tab
                },
            )
        }
        persistSoon()
    }

    fun updatePreferences(preferences: AppPreferences) {
        val safePreferences = preferences.copy(
            themeMode = preferences.themeMode.takeIf { it in setOf("system", "dark", "light") } ?: "system",
            accentColor = preferences.accentColor.takeIf { it in setOf("emerald", "blue", "amber") } ?: "emerald",
            fontScale = preferences.fontScale.coerceIn(0.9f, 1.2f),
        )
        _uiState.update { it.copy(preferences = safePreferences) }
        persistSoon()
    }

    fun saveDraft() {
        // Generate the tab title on an explicit save, not on every keystroke.
        val savedAt = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id != state.activeTabId) tab
                    else {
                        val generatedTitle = if (tab.isTitleCustom) {
                            tab.title
                        } else {
                            tab.draft.substringBefore('\n').trim().take(42).ifBlank { tab.title }
                        }
                        tab.copy(title = generatedTitle, updatedAt = savedAt)
                    }
                },
            )
        }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            persistCurrentState()
            _events.emit(AgentDockEvent.DraftSaved)
        }
    }

    fun saveServerUrl(value: String) {
        val normalized = normalizeServerAddress(value)
        if (normalized == null) {
            _events.tryEmit(AgentDockEvent.InvalidServerAddress)
            return
        }
        _uiState.update { it.copy(serverUrl = normalized) }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            persistCurrentState()
            _events.emit(AgentDockEvent.ServerAddressSaved)
        }
    }

    private fun persistSoon() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistCurrentState()
        }
    }

    private suspend fun persistCurrentState() {
        val state = _uiState.value
        if (!state.isLoaded || state.tabs.isEmpty()) return
        val activeId = state.tabs.firstOrNull { it.id == state.activeTabId }?.id ?: state.tabs.first().id
        repository.save(
            WorkspaceSnapshot(
                tabs = state.tabs,
                activeTabId = activeId,
                serverUrl = state.serverUrl,
                closedTabs = state.closedTabs,
                preferences = state.preferences,
            ),
        )
    }

    class Factory(
        private val repository: WorkspaceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AgentDockViewModel::class.java)) {
                return AgentDockViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }

    private companion object {
        const val MAX_CLOSED_TABS = 10
        const val PERSIST_DEBOUNCE_MS = 300L
    }
}
