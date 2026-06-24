package com.ounben.amaradio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalContext
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils

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
        darkColorScheme(
            primary = colorResource(R.color.colorAccentDark),
            onPrimary = colorResource(android.R.color.black),
            secondary = colorResource(R.color.colorPrimaryDark),
            onSecondary = colorResource(android.R.color.white),
            background = colorResource(R.color.windowBackgroundDark),
            onBackground = colorResource(android.R.color.white),
            surface = colorResource(R.color.colorPrimaryDark),
            onSurface = colorResource(android.R.color.white),
            surfaceVariant = colorResource(R.color.iconsInItemBackgroundColorDark),
            onSurfaceVariant = colorResource(R.color.textColorSecondary),
            outline = colorResource(R.color.colorAccentDark)
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.colorPrimary),
            onPrimary = colorResource(android.R.color.white),
            secondary = colorResource(R.color.colorAccent),
            onSecondary = colorResource(android.R.color.white),
            background = colorResource(R.color.windowBackground),
            onBackground = colorResource(android.R.color.black),
            surface = colorResource(android.R.color.white),
            onSurface = colorResource(android.R.color.black),
            surfaceVariant = colorResource(R.color.iconsInItemBackgroundColor),
            onSurfaceVariant = colorResource(R.color.textColorSecondary),
            outline = colorResource(R.color.colorPrimary)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content
            )
        }
    )
}
