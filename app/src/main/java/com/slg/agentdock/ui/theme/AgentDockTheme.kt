package com.slg.agentdock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkEmerald = darkColorScheme(
    primary = Color(0xFF65C995), onPrimary = Color(0xFF102218),
    secondary = Color(0xFFA2B0A7), onSecondary = Color(0xFF17221B),
    background = Color(0xFF111713), onBackground = Color(0xFFE7EEE9),
    surface = Color(0xFF19221C), onSurface = Color(0xFFE7EEE9),
    surfaceVariant = Color(0xFF202B24), onSurfaceVariant = Color(0xFFA2B0A7),
    outline = Color(0xFF3A4940), error = Color(0xFFE17C78),
)

private val LightEmerald = lightColorScheme(
    primary = Color(0xFF287A50), onPrimary = Color.White,
    secondary = Color(0xFF52645A), onSecondary = Color.White,
    background = Color(0xFFF4F7F4), onBackground = Color(0xFF18231C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF18231C),
    surfaceVariant = Color(0xFFE8EFE9), onSurfaceVariant = Color(0xFF52645A),
    outline = Color(0xFFD2DDD5), error = Color(0xFFBA1A1A),
)

private val DarkBlue = DarkEmerald.copy(
    primary = Color(0xFF91B5FF), onPrimary = Color(0xFF10264B),
    secondary = Color(0xFFAAB9D2), onSecondary = Color(0xFF202B3C),
)

private val LightBlue = LightEmerald.copy(
    primary = Color(0xFF365FA8), onPrimary = Color.White,
    secondary = Color(0xFF536987), onSecondary = Color.White,
)

private val DarkAmber = DarkEmerald.copy(
    primary = Color(0xFFE8B85D), onPrimary = Color(0xFF332306),
    secondary = Color(0xFFC5B18B), onSecondary = Color(0xFF302718),
)

private val LightAmber = LightEmerald.copy(
    primary = Color(0xFF91610A), onPrimary = Color.White,
    secondary = Color(0xFF75613D), onSecondary = Color.White,
)

@Composable
fun AgentDockTheme(
    themeMode: String = "system",
    accentColor: String = "emerald",
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when (accentColor) {
        "blue" -> if (darkTheme) DarkBlue else LightBlue
        "amber" -> if (darkTheme) DarkAmber else LightAmber
        else -> if (darkTheme) DarkEmerald else LightEmerald
    }
    val currentDensity = LocalDensity.current
    val adjustedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontScale.coerceIn(0.9f, 1.2f),
    )

    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
