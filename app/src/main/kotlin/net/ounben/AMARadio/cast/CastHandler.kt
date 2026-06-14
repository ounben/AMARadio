package net.ounben.AMARadio.cast

import android.content.Context
import android.view.Menu
import android.view.MenuItem

class CastHandler(private val context: Context) {

    interface CastHandlerListener {
        fun invalidateOptionsMenuForCast()
    }

    fun setActivity(activity: CastAwareActivity?) {}
    fun onResume() {}
    fun onPause() {}
    fun getRouteItem(context: Context, menu: Menu): MenuItem? = null
    val isCastSessionAvailable: Boolean get() = false
    fun playRemote(title: String, url: String, iconUrl: String?) {}
}
