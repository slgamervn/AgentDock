package com.slg.agentdock.ui

import com.slg.agentdock.data.model.AppPreferences
import com.slg.agentdock.data.model.WorkspaceSnapshot
import com.slg.agentdock.data.model.WorkspaceTab
import com.slg.agentdock.data.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class AgentDockViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsSavedWorkspaceAndServerAddress() = runTest(mainDispatcherRule.dispatcher) {
        val savedTab = WorkspaceTab(
            id = "saved-tab",
            title = "Existing task",
            draft = "Saved locally",
            isPinned = true,
            updatedAt = 123L,
            isTitleCustom = true,
        )
        val savedClosedTab = WorkspaceTab(id = "closed-tab", title = "Previous task")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(
                tabs = listOf(savedTab),
                activeTabId = savedTab.id,
                serverUrl = "https://agentdock.example/api",
                closedTabs = listOf(savedClosedTab),
            ),
        )

        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(listOf(savedTab), state.tabs)
        assertEquals(savedTab.id, state.activeTabId)
        assertEquals("https://agentdock.example/api", state.serverUrl)
        assertEquals(listOf(savedClosedTab), state.closedTabs)
    }

    @Test
    fun draftTextUpdatesOnlyTheActiveTabAndTitleIsGeneratedOnSave() = runTest(mainDispatcherRule.dispatcher) {
        val tab = WorkspaceTab(id = "tab-1")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(tabs = listOf(tab), activeTabId = tab.id),
        )
        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        val chromeBeforeTyping = viewModel.chromeState.value
        viewModel.updateDraft(tab.id, "  First line  \nSecond line")
        runCurrent()

        assertEquals(chromeBeforeTyping, viewModel.chromeState.value)
        assertEquals("  First line  \nSecond line", viewModel.activeTab.value?.draft)
        assertEquals("", viewModel.uiState.value.tabs.single().title)

        viewModel.saveDraft()
        runCurrent()
        assertEquals("First line", viewModel.uiState.value.tabs.single().title)

        viewModel.renameTab(tab.id, "  My   custom title  ")
        assertEquals("My custom title", viewModel.uiState.value.tabs.single().title)

        viewModel.updateDraft(tab.id, "A different task")
        assertEquals("My custom title", viewModel.uiState.value.tabs.single().title)
        assertEquals("A different task", viewModel.uiState.value.tabs.single().draft)

        viewModel.renameTab(tab.id, "")
        viewModel.updateDraft(tab.id, "Automatically named again")
        assertEquals("", viewModel.uiState.value.tabs.single().title)
        viewModel.saveDraft()
        runCurrent()
        assertEquals("Automatically named again", viewModel.uiState.value.tabs.single().title)
    }

    @Test
    fun closingAndRestoringActiveTabKeepsAValidSelection() = runTest(mainDispatcherRule.dispatcher) {
        val first = WorkspaceTab(id = "first", title = "First")
        val second = WorkspaceTab(id = "second", title = "Second")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(
                tabs = listOf(first, second),
                activeTabId = first.id,
            ),
        )
        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        viewModel.closeTab(first.id)
        assertEquals(listOf(second), viewModel.uiState.value.tabs)
        assertEquals(second.id, viewModel.uiState.value.activeTabId)
        assertEquals(listOf(first), viewModel.uiState.value.closedTabs)

        viewModel.restoreLastClosedTab()
        assertEquals(listOf(second, first), viewModel.uiState.value.tabs)
        assertEquals(first.id, viewModel.uiState.value.activeTabId)
        assertTrue(viewModel.uiState.value.closedTabs.isEmpty())

        advanceTimeBy(300)
        runCurrent()
    }

    @Test
    fun closingLastTabCreatesANewActiveTab() = runTest(mainDispatcherRule.dispatcher) {
        val onlyTab = WorkspaceTab(id = "only-tab")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(tabs = listOf(onlyTab), activeTabId = onlyTab.id),
        )
        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        viewModel.closeTab(onlyTab.id)

        val state = viewModel.uiState.value
        assertEquals(1, state.tabs.size)
        assertNotEquals(onlyTab.id, state.tabs.single().id)
        assertEquals(state.tabs.single().id, state.activeTabId)
        assertEquals(listOf(onlyTab), state.closedTabs)

        advanceTimeBy(300)
        runCurrent()
    }

    @Test
    fun draftPersistenceDebouncesAndSavesLatestValue() = runTest(mainDispatcherRule.dispatcher) {
        val tab = WorkspaceTab(id = "tab-1")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(tabs = listOf(tab), activeTabId = tab.id),
        )
        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        viewModel.updateDraft(tab.id, "Intermediate value")
        viewModel.updateDraft(tab.id, "Final value")

        advanceTimeBy(299)
        runCurrent()
        assertTrue(repository.savedSnapshots.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, repository.savedSnapshots.size)
        assertEquals("Final value", repository.savedSnapshots.single().tabs.single().draft)
        assertEquals("Final value", repository.savedSnapshots.single().tabs.single().title)
    }

    @Test
    fun personalizationPreferencesAreAppliedAndPersisted() = runTest(mainDispatcherRule.dispatcher) {
        val tab = WorkspaceTab(id = "tab-1")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(tabs = listOf(tab), activeTabId = tab.id),
        )
        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        val preferences = AppPreferences(
            themeMode = "light",
            accentColor = "blue",
            fontScale = 1.15f,
            soundEnabled = true,
            hapticsEnabled = false,
        )
        viewModel.updatePreferences(preferences)
        assertEquals(preferences, viewModel.uiState.value.preferences)

        advanceTimeBy(300)
        runCurrent()
        assertEquals(preferences, repository.savedSnapshots.last().preferences)
    }

    @Test
    fun personalizationPreferencesRejectUnknownModesAndClampTextScale() = runTest(mainDispatcherRule.dispatcher) {
        val tab = WorkspaceTab(id = "tab-1")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(tabs = listOf(tab), activeTabId = tab.id),
        )
        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        viewModel.updatePreferences(
            AppPreferences(
                themeMode = "invalid",
                accentColor = "invalid",
                fontScale = 5f,
            ),
        )
        val preferences = viewModel.uiState.value.preferences
        assertEquals("system", preferences.themeMode)
        assertEquals("emerald", preferences.accentColor)
        assertEquals(1.2f, preferences.fontScale)
    }

    @Test
    fun serverAddressIsNormalizedAndInvalidValueIsNotPersisted() = runTest(mainDispatcherRule.dispatcher) {
        val tab = WorkspaceTab(id = "tab-1")
        val repository = FakeWorkspaceRepository(
            WorkspaceSnapshot(tabs = listOf(tab), activeTabId = tab.id),
        )
        val viewModel = AgentDockViewModel(repository)
        runCurrent()

        viewModel.saveServerUrl(" https://agentdock.example/api/ ")
        runCurrent()
        assertEquals("https://agentdock.example/api", viewModel.uiState.value.serverUrl)
        assertEquals("https://agentdock.example/api", repository.savedSnapshots.last().serverUrl)

        val saveCount = repository.savedSnapshots.size
        viewModel.saveServerUrl("https://user:secret@agentdock.example")
        runCurrent()

        assertEquals("https://agentdock.example/api", viewModel.uiState.value.serverUrl)
        assertEquals(saveCount, repository.savedSnapshots.size)
    }
}

private class FakeWorkspaceRepository(
    initialSnapshot: WorkspaceSnapshot,
) : WorkspaceRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)
    override val snapshot: Flow<WorkspaceSnapshot> = mutableSnapshot.asStateFlow()
    val savedSnapshots = mutableListOf<WorkspaceSnapshot>()

    override suspend fun save(snapshot: WorkspaceSnapshot) {
        savedSnapshots += snapshot
        mutableSnapshot.value = snapshot
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
