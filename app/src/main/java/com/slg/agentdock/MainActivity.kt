package com.slg.agentdock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.slg.agentdock.ui.AgentDockApp
import com.slg.agentdock.ui.theme.AgentDockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentDockTheme {
                AgentDockApp()
            }
        }
    }
}
