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
val SolidRedError = Color(0xFFFF3333) // Solid Bright Red for Delete/Error in Dark Mode
val SolidRedErrorLight = Color(0xFFD32F2F) // Solid Red for Delete/Error in Light Mode

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
        // STRICT OPAQUE DARK (All containers & indicators forced to Amber / Neutral Grays / Solid Red)
        darkColorScheme(
            primary = AmaradioAmber,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF331E00),
            onPrimaryContainer = AmaradioAmber,
            secondary = DarkSurfaceVariant,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF2C2C2C),
            onSecondaryContainer = Color.White,
            tertiary = AmaradioAmber,
            onTertiary = Color.Black,
            tertiaryContainer = Color(0xFF331E00),
            onTertiaryContainer = AmaradioAmber,
            error = SolidRedError,
            onError = Color.White,
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            background = DarkBackground,
            onBackground = Color.White,
            surface = DarkSurface,
            onSurface = Color.White,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerHigh = Color(0xFF252525),
            surfaceContainerHighest = Color(0xFF2D2D2D),
            outline = DarkOutline,
            surfaceTint = Color.Transparent
        )
    } else {
        // STRICT OPAQUE LIGHT (All containers & indicators forced to Amber / Neutral Grays / Solid Red)
        lightColorScheme(
            primary = AmaradioAmber,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFFFFECB3),
            onPrimaryContainer = Color(0xFF3E2723),
            secondary = LightSurfaceVariant,
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFFE0E0E0),
            onSecondaryContainer = Color.Black,
            tertiary = AmaradioAmber,
            onTertiary = Color.Black,
            tertiaryContainer = Color(0xFFFFECB3),
            onTertiaryContainer = Color(0xFF3E2723),
            error = SolidRedErrorLight,
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            background = LightBackground,
            onBackground = Color.Black,
            surface = LightSurface,
            onSurface = Color.Black,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainer = Color(0xFFF5F5F5),
            surfaceContainerHigh = Color(0xFFEEEEEE),
            surfaceContainerHighest = Color(0xFFE0E0E0),
            outline = LightOutline,
            surfaceTint = Color.Transparent
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
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
