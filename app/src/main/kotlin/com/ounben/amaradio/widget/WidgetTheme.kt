package com.ounben.amaradio.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.material3.ColorProviders

object WidgetTheme {
    val Amber = Color(0xFFFF9800)
    
    val LightBackground = Color(0xFFFFFFFF)
    val LightTextPrimary = Color(0xFF0F0F0F)
    val LightTextSecondary = Color(0xFF606060)
    
    val DarkBackground = Color(0xFF000000)
    val DarkTextPrimary = Color(0xFFFFFFFF)
    val DarkTextSecondary = Color(0xFFAAAAAA)
    val DarkSurfaceVariant = Color(0xFF242424)

    val colors = ColorProviders(
        light = lightColorScheme(
            primary = Amber,
            onPrimary = Color.White,
            background = LightBackground,
            onBackground = LightTextPrimary,
            surface = LightBackground,
            onSurface = LightTextPrimary,
            surfaceVariant = Color(0xFFF2F2F2),
            onSurfaceVariant = LightTextSecondary,
            outline = Color(0xFFBDBDBD)
        ),
        dark = darkColorScheme(
            primary = Amber,
            onPrimary = Color.Black,
            background = DarkBackground,
            onBackground = DarkTextPrimary,
            surface = DarkBackground,
            onSurface = DarkTextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            outline = Color(0xFF444444)
        )
    )
}
