package com.slg.agentdock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.slg.agentdock.ui.AgentDockApp
import com.slg.agentdock.ui.AgentDockViewModel
import com.slg.agentdock.ui.theme.AgentDockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as AgentDockApplication).workspaceRepository
        val viewModel = ViewModelProvider(
            this,
            AgentDockViewModel.Factory(repository),
        )[AgentDockViewModel::class.java]

        setContent {
            AgentDockTheme {
                AgentDockApp(viewModel = viewModel)
            }
        }
    }
}
