package com.ounben.amaradio.utils

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager

object UiScaler {
    const val PREF_KEY_UI_SCALE = "ui_scale_level"
    
    const val SCALE_COMPACT = 0.85f
    const val SCALE_STANDARD = 1.0f
    const val SCALE_LARGE = 1.25f
    const val SCALE_EXTRA_LARGE = 1.5f

    fun getScaleFactor(context: Context): Float {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return when (prefs.getString(PREF_KEY_UI_SCALE, "standard")) {
            "compact" -> SCALE_COMPACT
            "large" -> SCALE_LARGE
            "extra_large" -> SCALE_EXTRA_LARGE
            else -> SCALE_STANDARD
        }
    }

    fun wrapContext(context: Context): Context {
        try {
            val factor = getScaleFactor(context)
            if (factor == SCALE_STANDARD) return context
            
            val config = Configuration(context.resources.configuration)
            config.fontScale = factor
            return context.createConfigurationContext(config)
        } catch (e: Exception) {
            return context
        }
    }

    /**
     * Scales a dimension value based on the current UI scale factor.
     */
    fun scaleDimension(context: Context, dimenResId: Int): Int {
        val original = context.resources.getDimensionPixelSize(dimenResId)
        return (original * getScaleFactor(context)).toInt()
    }

    /**
     * Directly scales a raw pixel value.
     */
    fun scaleValue(context: Context, value: Int): Int {
        return (value * getScaleFactor(context)).toInt()
    }

    fun scaleValue(context: Context, value: Float): Float {
        return value * getScaleFactor(context)
    }

    fun getGridColumnCount(context: Context): Int {
        val factor = getScaleFactor(context)
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val itemWidthDp = 100f * factor // base item width + padding
        return (screenWidthDp / itemWidthDp).toInt().coerceAtLeast(2)
    }
}
