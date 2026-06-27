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

// Solid Brand Color
val AmaradioAmber = Color(0xFFFF8F00) 

// Opaque Light Palette
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEBEBEB) // Solid Gray
val LightOnBackground = Color(0xFF000000)
val LightOnSurfaceVariant = Color(0xFF444444)
val LightOutline = Color(0xFFBDBDBD) // Solid Outline

// Opaque Dark Palette
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF242424) // Solid Dark Gray
val DarkOnBackground = Color(0xFFFFFFFF)
val DarkOnSurfaceVariant = Color(0xFFBBBBBB)
val DarkOutline = Color(0xFF444444) // Solid Outline

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
        // STRICT OPAQUE DARK
        darkColorScheme(
            primary = AmaradioAmber,
            onPrimary = Color.Black,
            secondary = DarkSurfaceVariant,
            onSecondary = Color.White,
            background = DarkBackground,
            onBackground = Color.White,
            surface = DarkSurface,
            onSurface = Color.White,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline,
            surfaceTint = Color.Transparent
        )
    } else {
        // STRICT OPAQUE LIGHT
        lightColorScheme(
            primary = AmaradioAmber,
            onPrimary = Color.White,
            secondary = LightSurfaceVariant,
            onSecondary = Color.Black,
            background = LightBackground,
            onBackground = Color.Black,
            surface = LightSurface,
            onSurface = Color.Black,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
            surfaceTint = Color.Transparent
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
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
