package com.ounben.amaradio.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleUtils {
    fun applyLocale(languageCode: String) {
        val appLocales = if (languageCode == "system") {
            val systemLocale = android.content.res.Resources.getSystem().configuration.locales[0]
            Locale.setDefault(systemLocale)
            LocaleListCompat.getEmptyLocaleList()
        } else {
            val tag = languageCode.replace("-r", "-")
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    fun wrapContext(context: Context, languageCode: String): Context {
        if (languageCode == "system") return context

        val locale = if (languageCode.contains("-")) {
            val tag = languageCode.replace("-r", "-")
            Locale.forLanguageTag(tag)
        } else {
            Locale.forLanguageTag(languageCode)
        }

        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }

    fun getLatinHintLocales(): androidx.compose.ui.text.intl.LocaleList? {
        val currentLocale = Locale.getDefault()
        val latinLangs = setOf(
            "en", "de", "fr", "es", "it", "pt", "nl", "sv", "nb", "da", "fi", "pl", 
            "cs", "sk", "hu", "ro", "tr", "id", "ms", "vi", "sq", "hr", "sr", "sl", "et", "lv", "lt"
        )
        
        return if (latinLangs.contains(currentLocale.language)) {
            null // Keep current (Western/Latin)
        } else {
            // Suggest English for non-Latin scripts (Arabic, Chinese, Russian, etc.)
            androidx.compose.ui.text.intl.LocaleList(androidx.compose.ui.text.intl.Locale("en"))
        }
    }
}
