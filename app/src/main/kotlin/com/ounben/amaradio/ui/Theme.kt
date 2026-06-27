package com.ounben.amaradio.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.ounben.amaradio.Utils
import com.ounben.amaradio.utils.UiScaler

// Enhanced Amaradio Brand Color
val AmaradioAmber = Color(0xFFFF8F00) 

// YouTube-inspired Palette
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF9F9F9)
val LightOnBackground = Color(0xFF0F0F0F)
val LightOnSurfaceVariant = Color(0xFF606060)

val DarkBackground = Color(0xFF0F0F0F)
val DarkSurface = Color(0xFF0F0F0F)
val DarkSurfaceVariant = Color(0xFF212121)
val DarkOnBackground = Color(0xFFFFFFFF)
val DarkOnSurfaceVariant = Color(0xFFAAAAAA)

@Composable
fun AMARadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val useDarkTheme = when (Utils.getTheme(context)) {
        "dark" -> true
        "light" -> false
        else -> darkTheme
    }

    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = AmaradioAmber,
            onPrimary = Color.Black,
            secondary = DarkSurfaceVariant,
            onSecondary = DarkOnBackground,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnBackground,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = AmaradioAmber.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = AmaradioAmber,
            onPrimary = Color.White,
            secondary = LightSurfaceVariant,
            onSecondary = LightOnBackground,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnBackground,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = AmaradioAmber.copy(alpha = 0.5f)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    val scale = UiScaler.getScaleFactor(context)
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density * scale,
        fontScale = currentDensity.fontScale * scale
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    content = content
                )
            }
        }
    )
}
