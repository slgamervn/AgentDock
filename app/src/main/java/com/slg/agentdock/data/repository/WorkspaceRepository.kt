package com.slg.agentdock.data.repository

import android.content.Context
import com.slg.agentdock.data.local.WorkspaceStore
import com.slg.agentdock.data.model.WorkspaceSnapshot
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    val snapshot: Flow<WorkspaceSnapshot>
    suspend fun save(snapshot: WorkspaceSnapshot)
}

class DefaultWorkspaceRepository(context: Context) : WorkspaceRepository {
    private val localStore = WorkspaceStore(context.applicationContext)

    override val snapshot: Flow<WorkspaceSnapshot> = localStore.snapshot

    override suspend fun save(snapshot: WorkspaceSnapshot) {
        localStore.save(snapshot)
    }
}
