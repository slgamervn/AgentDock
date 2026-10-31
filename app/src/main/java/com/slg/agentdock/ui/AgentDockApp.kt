package com.slg.agentdock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slg.agentdock.R
import com.slg.agentdock.data.model.WorkspaceTab
import kotlinx.coroutines.flow.collect

private const val ROUTE_HOME = "home"
private const val ROUTE_TABS = "tabs"
private const val ROUTE_WORKSPACE = "workspace"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun AgentDockApp(viewModel: AgentDockViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val draftSavedMessage = stringResource(R.string.draft_saved)
    val serverAddressSavedMessage = stringResource(R.string.server_address_saved)
    val invalidServerAddressMessage = stringResource(R.string.invalid_server_address)

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

    if (!state.isLoaded || state.tabs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val activeTab = state.tabs.firstOrNull { it.id == state.activeTabId } ?: state.tabs.first()

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
            MainNavigationBar(
                currentRoute = currentRoute,
                onNavigate = ::navigateTo,
            )
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
                tabs = state.tabs,
                activeTabId = activeTab.id,
                onSelect = { tabId ->
                    viewModel.selectTab(tabId)
                    navigateTo(ROUTE_WORKSPACE)
                },
                onAdd = {
                    viewModel.addTab()
                    navigateTo(ROUTE_WORKSPACE)
                },
                onShowAll = { navigateTo(ROUTE_TABS) },
            )
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None },
            ) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        tab = activeTab,
                        onDraftChange = viewModel::updateDraft,
                        onSaveDraft = {
                            viewModel.saveDraft()
                            navigateTo(ROUTE_WORKSPACE)
                        },
                    )
                }
                composable(ROUTE_TABS) {
                    TabsScreen(
                        tabs = state.tabs,
                        closedTabs = state.closedTabs,
                        activeTabId = activeTab.id,
                        onOpen = { tabId ->
                            viewModel.selectTab(tabId)
                            navigateTo(ROUTE_WORKSPACE)
                        },
                        onAdd = {
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
                    WorkspaceScreen(
                        tab = activeTab,
                        onDraftChange = viewModel::updateDraft,
                        onSaveDraft = viewModel::saveDraft,
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        serverUrl = state.serverUrl,
                        onSaveServerUrl = viewModel::saveServerUrl,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.local_label),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun WorkspaceTabStrip(
    tabs: List<WorkspaceTab>,
    activeTabId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onShowAll: () -> Unit,
) {
    val showAllTabsLabel = stringResource(R.string.show_all_tabs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = tab.id == activeTabId
                val tabLabel = if (tab.title.isBlank()) {
                    "${stringResource(R.string.new_task)} ${index + 1}"
                } else {
                    tab.title
                }
                Surface(
                    onClick = { onSelect(tab.id) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                ) {
                    Text(
                        text = tabLabel,
                        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 9.dp),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_tab))
        }
        IconButton(
            onClick = onShowAll,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = showAllTabsLabel },
        ) {
            Text(
                text = tabs.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    tab: WorkspaceTab,
    onDraftChange: (String, String) -> Unit,
    onSaveDraft: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.home_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.task_prompt_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tab.draft,
                    onValueChange = { onDraftChange(tab.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.task_prompt_hint)) },
                    minLines = 5,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSaveDraft,
                    enabled = tab.draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(vertical = 13.dp),
                ) {
                    Text(stringResource(R.string.save_draft), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.local_draft_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.recent_tasks),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        EmptyState(
            title = stringResource(R.string.no_tasks_title),
            description = stringResource(R.string.no_tasks_description),
        )
    }
}

@Composable
private fun WorkspaceScreen(
    tab: WorkspaceTab,
    onDraftChange: (String, String) -> Unit,
    onSaveDraft: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = tab.title.ifBlank { stringResource(R.string.new_task) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = stringResource(R.string.draft_status),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.saved_on_device),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.task_definition),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tab.draft,
            onValueChange = { onDraftChange(tab.id, it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.task_prompt_hint)) },
            minLines = 4,
            maxLines = 9,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSaveDraft,
            enabled = tab.draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            Text(stringResource(R.string.save_draft))
        }
        Spacer(Modifier.height(22.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.backend_not_ready_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.backend_not_ready_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.workspace_sections),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.workspace_sections_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TabsScreen(
    tabs: List<WorkspaceTab>,
    closedTabs: List<WorkspaceTab>,
    activeTabId: String,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onClose: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRestoreLastClosed: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredTabs = tabs
        .filter { tab ->
            query.isBlank() || tab.title.contains(query, ignoreCase = true) || tab.draft.contains(query, ignoreCase = true)
        }
        .sortedWith(compareByDescending<WorkspaceTab> { it.isPinned }.thenByDescending { it.updatedAt })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tabs_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.tabs_count, tabs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onRestoreLastClosed,
                    enabled = closedTabs.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.restore_closed_tab))
                }
                TextButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.new_tab))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_tabs)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (filteredTabs.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_matching_tabs),
                description = stringResource(R.string.no_matching_tabs_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(filteredTabs, key = WorkspaceTab::id) { tab ->
                    TabListItem(
                        tab = tab,
                        selected = tab.id == activeTabId,
                        onOpen = { onOpen(tab.id) },
                        onClose = { onClose(tab.id) },
                        onTogglePinned = { onTogglePinned(tab.id) },
                        onRename = { title -> onRename(tab.id, title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabListItem(
    tab: WorkspaceTab,
    selected: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onTogglePinned: () -> Unit,
    onRename: (String) -> Unit,
) {
    val tabTitle = if (tab.title.isBlank()) stringResource(R.string.new_task) else tab.title
    val draftPreview = tab.draft.lineSequence().firstOrNull().orEmpty()
    val tabSubtitle = if (draftPreview.isBlank()) stringResource(R.string.empty_draft) else draftPreview
    var showRenameDialog by rememberSaveable(tab.id) { mutableStateOf(false) }
    var editableTitle by rememberSaveable(tab.id) { mutableStateOf(tab.title) }

    Surface(
        onClick = onOpen,
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 14.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    text = tabTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = tabSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { editableTitle = tab.title; showRenameDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename_tab))
            }
            IconButton(onClick = onTogglePinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = stringResource(if (tab.isPinned) R.string.unpin_tab else R.string.pin_tab),
                    tint = if (tab.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close_tab),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_tab)) },
            text = {
                OutlinedTextField(
                    value = editableTitle,
                    onValueChange = { editableTitle = it.take(48) },
                    label = { Text(stringResource(R.string.rename_tab_title)) },
                    placeholder = { Text(stringResource(R.string.rename_tab_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(editableTitle)
                        showRenameDialog = false
                    },
                    enabled = editableTitle.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsScreen(
    serverUrl: String,
    onSaveServerUrl: (String) -> Unit,
) {
    var address by remember(serverUrl) { mutableStateOf(serverUrl) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.server_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.server_url_label)) },
            placeholder = { Text(stringResource(R.string.server_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.server_url_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSaveServerUrl(address) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_settings))
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(18.dp))
        SettingsRow(
            title = stringResource(R.string.ai_provider_title),
            detail = stringResource(R.string.not_configured_yet),
        )
        Spacer(Modifier.height(14.dp))
        SettingsRow(
            title = stringResource(R.string.integrations_title),
            detail = stringResource(R.string.integrations_not_ready),
        )
        Spacer(Modifier.height(14.dp))
        SettingsRow(
            title = stringResource(R.string.security_title),
            detail = stringResource(R.string.security_summary),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.settings_version),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsRow(title: String, detail: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MainNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = currentRoute == ROUTE_HOME,
            onClick = { onNavigate(ROUTE_HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.home_nav)) },
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_TABS,
            onClick = { onNavigate(ROUTE_TABS) },
            icon = { Icon(Icons.Filled.List, contentDescription = null) },
            label = { Text(stringResource(R.string.tabs_nav)) },
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_SETTINGS,
            onClick = { onNavigate(ROUTE_SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.settings_nav)) },
        )
    }
}
