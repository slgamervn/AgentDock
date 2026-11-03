
package com.slg.agentdock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slg.agentdock.R
import com.slg.agentdock.ui.WorkspaceTabSummary

@Composable
fun WorkspaceTabStrip(
    tabs: List<WorkspaceTabSummary>,
    activeTabId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onShowAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                items = tabs,
                key = { _, tab -> tab.id },
                contentType = { _, _ -> "workspace-tab" },
            ) { index, tab ->
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
        IconButton(
            onClick = onAdd,
            modifier = Modifier.size(48.dp).testTag("tabStrip.add"),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_tab))
        }
        IconButton(
            onClick = onShowAll,
            modifier = Modifier.size(48.dp).testTag("tabStrip.openAll"),
        ) {
            Text(
                text = tabs.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
