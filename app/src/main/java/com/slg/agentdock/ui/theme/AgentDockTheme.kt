package com.slg.agentdock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AgentDockColors = darkColorScheme(
    primary = Color(0xFF65C995),
    onPrimary = Color(0xFF102218),
    secondary = Color(0xFFA2B0A7),
    onSecondary = Color(0xFF17221B),
    background = Color(0xFF111713),
    onBackground = Color(0xFFE7EEE9),
    surface = Color(0xFF19221C),
    onSurface = Color(0xFFE7EEE9),
    surfaceVariant = Color(0xFF202B24),
    onSurfaceVariant = Color(0xFFA2B0A7),
    outline = Color(0xFF303C34),
    error = Color(0xFFE17C78),
)

@Composable
fun AgentDockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgentDockColors,
        content = content,
    )
}
