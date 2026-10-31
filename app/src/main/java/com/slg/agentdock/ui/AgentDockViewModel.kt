package com.slg.agentdock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AgentDockUiState(
    val tabs: List<WorkspaceTab> = emptyList(),
    val activeTabId: String = "",
    val serverUrl: String = "",
    val closedTabs: List<WorkspaceTab> = emptyList(),
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
                        val autoTitle = value.lineSequence().firstOrNull().orEmpty().trim().take(42)
                        tab.copy(
                            title = if (tab.isTitleCustom) tab.title else autoTitle,
                            draft = value,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
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
            throw IllegalArgumentException("Unknown ViewModel class: \${modelClass.name}")
        }
    }

    private companion object {
        const val MAX_CLOSED_TABS = 10
        const val PERSIST_DEBOUNCE_MS = 300L
    }
}
