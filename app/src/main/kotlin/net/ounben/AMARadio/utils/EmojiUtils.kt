package net.ounben.AMARadio.utils

object EmojiUtils {
    /**
     * Converts a 2-letter country code to a flag emoji.
     * Example: "US" -> 🇺🇸
     */
    fun getFlagEmoji(countryCode: String?): String? {
        if (countryCode == null || countryCode.length != 2) return null
        
        val firstChar = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}
