package com.slg.agentdock.data.model

import java.util.UUID

data class WorkspaceTab(
    val id: String,
    val title: String = "",
    val draft: String = "",
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val isTitleCustom: Boolean = false,
)

data class AppPreferences(
    val themeMode: String = "system",
    val accentColor: String = "emerald",
    val fontScale: Float = 1f,
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
)

data class WorkspaceSnapshot(
    val tabs: List<WorkspaceTab>,
    val activeTabId: String,
    val serverUrl: String = "",
    val closedTabs: List<WorkspaceTab> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
)

fun defaultWorkspaceSnapshot(): WorkspaceSnapshot {
    val firstTab = WorkspaceTab(id = UUID.randomUUID().toString())
    return WorkspaceSnapshot(tabs = listOf(firstTab), activeTabId = firstTab.id)
}
