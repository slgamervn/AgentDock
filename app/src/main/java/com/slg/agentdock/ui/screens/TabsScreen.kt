
package com.slg.agentdock.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slg.agentdock.R
import com.slg.agentdock.data.model.WorkspaceTab
import com.slg.agentdock.ui.components.EmptyState

@Composable
fun TabsScreen(
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
    val filteredTabs = remember(tabs, query) {
        tabs
            .filter { tab ->
                query.isBlank() || tab.title.contains(query, ignoreCase = true) ||
                    tab.draft.contains(query, ignoreCase = true)
            }
            .sortedWith(compareByDescending<WorkspaceTab> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tabs.screen")
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
                    modifier = Modifier.testTag("tabs.restoreClosed"),
                    onClick = onRestoreLastClosed,
                    enabled = closedTabs.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.restore_closed_tab))
                }
                TextButton(
                    modifier = Modifier.testTag("tabs.newTab"),
                    onClick = onAdd,
                ) {
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
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = { editableTitle = tab.title; showRenameDialog = true },
            ) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename_tab))
            }
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = onTogglePinned,
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = stringResource(if (tab.isPinned) R.string.unpin_tab else R.string.pin_tab),
                    tint = if (tab.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = onClose,
            ) {
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
                    Text(stringResource(R.string.rename))
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
