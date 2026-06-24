package com.ounben.amaradio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ounben.amaradio.Utils

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D), // colorAccentDark (Amber)
    onPrimary = Color.Black,
    secondary = Color(0xFFFF9800), // colorPrimary (Orange)
    onSecondary = Color.Black,
    tertiary = Color(0xFFFF5722), // colorAccent (Deep Orange)
    background = Color(0xFF121212), // windowBackgroundDark
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color.LightGray,
    outline = Color(0xFFFFB74D) // Border color
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFF9800), // colorPrimary (Orange)
    onPrimary = Color.White,
    secondary = Color(0xFFFFB74D), // colorAccentDark (Amber)
    onSecondary = Color.Black,
    tertiary = Color(0xFFFF5722), // colorAccent (Deep Orange)
    background = Color(0xFFFFFFFF), // windowBackground
    onBackground = Color.Black,
    surface = Color(0xFFFFFFFF),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color.DarkGray,
    outline = Color(0xFFFF9800)
)

@Composable
fun AMARadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Determine if we should use dark theme based on app settings
    val useDarkTheme = when (Utils.getTheme(context)) {
        "dark" -> true
        "light" -> false
        else -> darkTheme
    }

    val colorScheme = if (useDarkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
