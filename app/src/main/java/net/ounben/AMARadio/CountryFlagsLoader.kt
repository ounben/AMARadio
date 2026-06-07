package net.ounben.AMARadio

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import java.util.*

class CountryFlagsLoader private constructor() {
    fun getFlag(context: Context, countryCode: String?): Drawable? {
        if (countryCode != null) {
            val resources = context.resources
            val resourceName = "flag_" + countryCode.lowercase(Locale.ROOT)
            val resourceId = resources.getIdentifier(resourceName, "drawable", context.packageName)
            if (resourceId != 0) {
                return ContextCompat.getDrawable(context, resourceId)
            }
        }
        return null
    }

    companion object {
        @JvmStatic
        val instance = CountryFlagsLoader()
    }
}
