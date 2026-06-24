package com.ounben.amaradio.proxy

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.Proxy

@Serializable
class ProxySettings {
    var host: String = ""
    var port: Int = 0
    var login: String = ""
    var password: String = ""
    var type: Proxy.Type? = null

    fun toPreferences(sharedPrefEditor: SharedPreferences.Editor) {
        val jsonStr = Json.encodeToString(this)
        sharedPrefEditor.putString(PREFERENCES_KEY, jsonStr)
    }

    companion object {
        private const val PREFERENCES_KEY = "proxySettings"

        @JvmStatic
        fun fromPreferences(sharedPref: SharedPreferences): ProxySettings? {
            val jsonStr = sharedPref.getString(PREFERENCES_KEY, "") ?: ""
            if (jsonStr.isBlank()) return null
            return try {
                Json.decodeFromString<ProxySettings>(jsonStr)
            } catch (_: Exception) {
                null
            }
        }
    }
}
