package net.ounben.AMARadio

import android.content.Context
import android.graphics.drawable.Drawable
import net.ounben.AMARadio.utils.EmojiUtils
import net.ounben.AMARadio.views.EmojiDrawable

class CountryFlagsLoader private constructor() {
    fun getFlag(context: Context, countryCode: String?): Drawable? {
        val emoji = EmojiUtils.getFlagEmoji(countryCode)
        return if (emoji != null) EmojiDrawable(emoji) else null
    }

    companion object {
        @JvmStatic
        val instance = CountryFlagsLoader()
    }
}
