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

// Defined Brand Color - Used strictly for highlights
val AmaradioAmber = Color(0xFFFF8F00) 

// Factual, Neutral Palette (No primary color mixing)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF2F2F2) // Neutral Light Gray
val LightOnBackground = Color(0xFF1A1A1A)
val LightOnSurfaceVariant = Color(0xFF505050)
val LightOutline = Color(0xFFD1D1D1)

val DarkBackground = Color(0xFF000000) // True Black for better contrast
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E1E) // Neutral Dark Gray
val DarkOnBackground = Color(0xFFEEEEEE)
val DarkOnSurfaceVariant = Color(0xFF9E9E9E)
val DarkOutline = Color(0xFF333333)

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
            outline = DarkOutline,
            surfaceTint = Color.Transparent // CRITICAL: Disables the "pinkish" bleed
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
            outline = LightOutline,
            surfaceTint = Color.Transparent // CRITICAL: Disables the "pinkish" bleed
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
