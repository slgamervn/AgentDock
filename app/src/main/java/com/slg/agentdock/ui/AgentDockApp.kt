package com.slg.agentdock.ui

import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slg.agentdock.R
import com.slg.agentdock.ui.components.AppHeader
import com.slg.agentdock.ui.components.MainNavigationBar
import com.slg.agentdock.ui.components.WorkspaceTabStrip
import com.slg.agentdock.ui.screens.HomeScreen
import com.slg.agentdock.ui.screens.SettingsScreen
import com.slg.agentdock.ui.screens.TabsScreen
import com.slg.agentdock.ui.screens.WorkspaceScreen
import com.slg.agentdock.ui.theme.AgentDockTheme
import kotlinx.coroutines.flow.collect

private const val ROUTE_HOME = "home"
private const val ROUTE_TABS = "tabs"
private const val ROUTE_WORKSPACE = "workspace"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun AgentDockApp(viewModel: AgentDockViewModel) {
    val chromeState by viewModel.chromeState.collectAsStateWithLifecycle()
    AgentDockTheme(
        themeMode = chromeState.preferences.themeMode,
        accentColor = chromeState.preferences.accentColor,
        fontScale = chromeState.preferences.fontScale,
    ) {
        AgentDockAppContent(viewModel, chromeState)
    }
}

@Composable
private fun AgentDockAppContent(
    viewModel: AgentDockViewModel,
    chromeState: AgentDockChromeState,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val draftSavedMessage = androidx.compose.ui.res.stringResource(R.string.draft_saved)
    val serverAddressSavedMessage = androidx.compose.ui.res.stringResource(R.string.server_address_saved)
    val invalidServerAddressMessage = androidx.compose.ui.res.stringResource(R.string.invalid_server_address)

    fun feedback() {
        if (chromeState.preferences.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        if (chromeState.preferences.soundEnabled) {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    LaunchedEffect(viewModel, draftSavedMessage, serverAddressSavedMessage, invalidServerAddressMessage) {
        viewModel.events.collect { event ->
            val message = when (event) {
                AgentDockEvent.DraftSaved -> draftSavedMessage
                AgentDockEvent.ServerAddressSaved -> serverAddressSavedMessage
                AgentDockEvent.InvalidServerAddress -> invalidServerAddressMessage
            }
            snackbarHostState.showSnackbar(message = message)
        }
    }

    if (!chromeState.isLoaded || chromeState.tabs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(R.string.loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val activeTabId = chromeState.activeTabId

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            MainNavigationBar(currentRoute = currentRoute, onNavigate = ::navigateTo)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppHeader()
            WorkspaceTabStrip(
                tabs = chromeState.tabs,
                activeTabId = activeTabId,
                onSelect = { tabId ->
                    viewModel.selectTab(tabId)
                    navigateTo(ROUTE_WORKSPACE)
                },
                onAdd = {
                    feedback()
                    viewModel.addTab()
                    navigateTo(ROUTE_WORKSPACE)
                },
                onShowAll = { navigateTo(ROUTE_TABS) },
            )
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None },
            ) {
                composable(ROUTE_HOME) {
                    HomeDestination(
                        viewModel = viewModel,
                        onDraftChange = viewModel::updateDraft,
                        onSaveDraft = {
                            feedback()
                            viewModel.saveDraft()
                            navigateTo(ROUTE_WORKSPACE)
                        },
                    )
                }
                composable(ROUTE_TABS) {
                    TabsDestination(
                        viewModel = viewModel,
                        onOpen = { tabId ->
                            viewModel.selectTab(tabId)
                            navigateTo(ROUTE_WORKSPACE)
                        },
                        onAdd = {
                            feedback()
                            viewModel.addTab()
                            navigateTo(ROUTE_WORKSPACE)
                        },
                        onClose = viewModel::closeTab,
                        onTogglePinned = viewModel::togglePinned,
                        onRename = viewModel::renameTab,
                        onRestoreLastClosed = viewModel::restoreLastClosedTab,
                    )
                }
                composable(ROUTE_WORKSPACE) {
                    WorkspaceDestination(
                        viewModel = viewModel,
                        onDraftChange = viewModel::updateDraft,
                        onSaveDraft = {
                            feedback()
                            viewModel.saveDraft()
                        },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsDestination(
                        viewModel = viewModel,
                        onSaveServerUrl = {
                            feedback()
                            viewModel.saveServerUrl(it)
                        },
                        onPreferencesChange = viewModel::updatePreferences,
                    )
                }
            }
        }
    }
}


@Composable
private fun HomeDestination(
    viewModel: AgentDockViewModel,
    onDraftChange: (String, String) -> Unit,
    onSaveDraft: () -> Unit,
) {
    val tab by viewModel.activeTab.collectAsStateWithLifecycle()
    tab?.let { HomeScreen(tab = it, onDraftChange = onDraftChange, onSaveDraft = onSaveDraft) }
}

@Composable
private fun WorkspaceDestination(
    viewModel: AgentDockViewModel,
    onDraftChange: (String, String) -> Unit,
    onSaveDraft: () -> Unit,
) {
    val tab by viewModel.activeTab.collectAsStateWithLifecycle()
    tab?.let { WorkspaceScreen(tab = it, onDraftChange = onDraftChange, onSaveDraft = onSaveDraft) }
}

@Composable
private fun TabsDestination(
    viewModel: AgentDockViewModel,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onClose: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRestoreLastClosed: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TabsScreen(
        tabs = state.tabs,
        closedTabs = state.closedTabs,
        activeTabId = state.activeTabId,
        onOpen = onOpen,
        onAdd = onAdd,
        onClose = onClose,
        onTogglePinned = onTogglePinned,
        onRename = onRename,
        onRestoreLastClosed = onRestoreLastClosed,
    )
}

@Composable
private fun SettingsDestination(
    viewModel: AgentDockViewModel,
    onSaveServerUrl: (String) -> Unit,
    onPreferencesChange: (com.slg.agentdock.data.model.AppPreferences) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        serverUrl = state.serverUrl,
        preferences = state.preferences,
        onSaveServerUrl = onSaveServerUrl,
        onPreferencesChange = onPreferencesChange,
    )
}
