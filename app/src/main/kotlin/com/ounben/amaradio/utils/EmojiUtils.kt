package com.ounben.amaradio.utils

import java.util.Locale

object EmojiUtils {
    /**
     * Converts a 2-letter country code to a flag emoji.
     * Example: "US" -> 🇺🇸
     */
    fun getFlagEmoji(countryCode: String?): String? {
        if (countryCode == null || countryCode.length != 2) return null
        // Country codes are ASCII identifiers: never let the device locale turn 'i' into 'İ'.
        val code = countryCode.uppercase(Locale.ROOT)

        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}
