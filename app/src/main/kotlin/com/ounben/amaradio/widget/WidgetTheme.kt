package com.ounben.amaradio.widget

import android.content.Context
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import com.ounben.amaradio.Utils
import com.ounben.amaradio.ui.AmaradioDarkColorScheme
import com.ounben.amaradio.ui.AmaradioLightColorScheme

/**
 * Bridges AMARadio's single-source-of-truth Material 3 color schemes from `Theme.kt`
 * into Glance [ColorProviders] while respecting the user's app theme preference.
 */
object WidgetTheme {
    private val systemColors = ColorProviders(
        light = AmaradioLightColorScheme,
        dark = AmaradioDarkColorScheme
    )

    private val forcedDarkColors = ColorProviders(
        light = AmaradioDarkColorScheme,
        dark = AmaradioDarkColorScheme
    )

    private val forcedLightColors = ColorProviders(
        light = AmaradioLightColorScheme,
        dark = AmaradioLightColorScheme
    )

    fun colors(context: Context): ColorProviders = when (Utils.getTheme(context)) {
        "dark" -> forcedDarkColors
        "light" -> forcedLightColors
        else -> systemColors
    }
}
