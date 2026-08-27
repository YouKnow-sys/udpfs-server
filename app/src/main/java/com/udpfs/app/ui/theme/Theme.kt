package com.udpfs.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme

private val UdpfsColorScheme = darkColorScheme(
    primary = Color(0xFF6FE0A2),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF00532F),
    onPrimaryContainer = Color(0xFF9CF6C0),
    secondary = Color(0xFF9ACBF2),
    onSecondary = Color(0xFF08344D),
    secondaryContainer = Color(0xFF1F4864),
    onSecondaryContainer = Color(0xFFCDE6FF),
    tertiary = Color(0xFFF3C36D),
    onTertiary = Color(0xFF412C00),
    tertiaryContainer = Color(0xFF5E4100),
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF490003),
    errorContainer = Color(0xFF5C1210),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF0A0E13),
    onBackground = Color(0xFFDFE5EB),
    surface = Color(0xFF0F141A),
    onSurface = Color(0xFFDFE5EB),
    surfaceVariant = Color(0xFF1B232D),
    onSurfaceVariant = Color(0xFFA9B6C4),
    outline = Color(0xFF5A6775),
    outlineVariant = Color(0xFF2C3641),
)

@Composable
fun udpfsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = UdpfsColorScheme, content = content)
}
