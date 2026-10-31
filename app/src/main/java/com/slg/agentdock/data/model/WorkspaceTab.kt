package com.slg.agentdock.data.model

import java.util.UUID

data class WorkspaceTab(
    val id: String,
    val title: String = "",
    val draft: String = "",
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class WorkspaceSnapshot(
    val tabs: List<WorkspaceTab>,
    val activeTabId: String,
    val serverUrl: String = "",
)

fun defaultWorkspaceSnapshot(): WorkspaceSnapshot {
    val firstTab = WorkspaceTab(id = UUID.randomUUID().toString())
    return WorkspaceSnapshot(tabs = listOf(firstTab), activeTabId = firstTab.id)
}
