package com.ounben.amaradio

import android.content.Context
import android.graphics.drawable.Drawable
import com.ounben.amaradio.utils.EmojiUtils
import com.ounben.amaradio.views.EmojiDrawable

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
