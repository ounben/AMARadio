package net.ounben.AMARadio.proxy

import android.content.SharedPreferences
import com.google.gson.Gson
import java.net.Proxy

class ProxySettings {
    var host: String = ""
    var port: Int = 0
    var login: String = ""
    var password: String = ""
    var type: Proxy.Type? = null

    fun toPreferences(sharedPrefEditor: SharedPreferences.Editor) {
        val gson = Gson()
        val jsonStr = gson.toJson(this)
        sharedPrefEditor.putString(PREFERENCES_KEY, jsonStr)
    }

    companion object {
        private const val PREFERENCES_KEY = "proxySettings"

        @JvmStatic
        fun fromPreferences(sharedPref: SharedPreferences): ProxySettings? {
            val gson = Gson()
            val jsonStr = sharedPref.getString(PREFERENCES_KEY, "")
            return gson.fromJson(jsonStr, ProxySettings::class.java)
        }
    }
}
