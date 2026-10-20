package com.freemusic.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1ED760)

private val Colors = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF282828),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB3B3B3),
)

@Composable
fun FreeMusicTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Colors, content = content)
