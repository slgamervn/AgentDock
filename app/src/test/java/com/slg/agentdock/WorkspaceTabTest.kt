package com.slg.agentdock

import com.slg.agentdock.data.model.defaultWorkspaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTabTest {
    @Test
    fun defaultWorkspaceHasOneActiveTab() {
        val snapshot = defaultWorkspaceSnapshot()

        assertEquals(1, snapshot.tabs.size)
        assertEquals(snapshot.tabs.first().id, snapshot.activeTabId)
    }

    @Test
    fun tabIdsAreUniqueWhenCreatedFromDefaultFactory() {
        val first = defaultWorkspaceSnapshot()
        val second = defaultWorkspaceSnapshot()

        assertTrue(first.tabs.first().id.isNotBlank())
        assertTrue(second.tabs.first().id.isNotBlank())
        assertTrue(first.tabs.first().id != second.tabs.first().id)
    }
}
