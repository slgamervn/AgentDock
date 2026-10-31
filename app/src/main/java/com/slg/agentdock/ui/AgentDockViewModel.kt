package com.slg.agentdock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slg.agentdock.data.local.WorkspaceStore
import com.slg.agentdock.data.model.WorkspaceSnapshot
import com.slg.agentdock.data.model.WorkspaceTab
import com.slg.agentdock.domain.validation.normalizeServerAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AgentDockUiState(
    val tabs: List<WorkspaceTab> = emptyList(),
    val activeTabId: String = "",
    val serverUrl: String = "",
    val isLoaded: Boolean = false,
)

sealed interface AgentDockEvent {
    data object DraftSaved : AgentDockEvent
    data object ServerAddressSaved : AgentDockEvent
    data object InvalidServerAddress : AgentDockEvent
}

class AgentDockViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkspaceStore(application)
    private val _uiState = MutableStateFlow(AgentDockUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AgentDockEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = store.snapshot.first()
            _uiState.update {
                it.copy(
                    tabs = saved.tabs,
                    activeTabId = saved.activeTabId,
                    serverUrl = saved.serverUrl,
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
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id != tabId) tab
                    else {
                        val autoTitle = value.lineSequence().firstOrNull().orEmpty().trim().take(34)
                        tab.copy(
                            title = autoTitle,
                            draft = value,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                },
            )
        }
        persistSoon()
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            if (state.tabs.size <= 1) {
                val replacement = WorkspaceTab(id = UUID.randomUUID().toString())
                state.copy(tabs = listOf(replacement), activeTabId = replacement.id)
            } else {
                val remaining = state.tabs.filterNot { it.id == tabId }
                val nextActive = if (state.activeTabId == tabId) {
                    remaining.getOrNull(0)?.id.orEmpty()
                } else state.activeTabId
                state.copy(tabs = remaining, activeTabId = nextActive)
            }
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

    fun saveDraft() {
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
            delay(300)
            persistCurrentState()
        }
    }

    private suspend fun persistCurrentState() {
        val state = _uiState.value
        if (!state.isLoaded || state.tabs.isEmpty()) return
        val activeId = state.tabs.firstOrNull { it.id == state.activeTabId }?.id ?: state.tabs.first().id
        store.save(
            WorkspaceSnapshot(
                tabs = state.tabs,
                activeTabId = activeId,
                serverUrl = state.serverUrl,
            ),
        )
    }
}
