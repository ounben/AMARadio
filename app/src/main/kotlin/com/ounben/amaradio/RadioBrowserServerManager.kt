package com.ounben.amaradio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Random

object RadioBrowserServerManager {
    private var currentServer: String? = null
    private val defaultServers = arrayOf(
        "de1.api.radio-browser.info"
    )

    /**
     * Non-blocking: Select a server if none is selected.
     */
    @JvmStatic
    suspend fun getCurrentServer(): String? = withContext(Dispatchers.Default) {
        if (currentServer == null) {
            currentServer = defaultServers[0]
            Log.d("SRV", "Selected initial server: $currentServer")
        }
        currentServer
    }

    /**
     * Returns the list of hardcoded reliable servers.
     */
    @JvmStatic
    fun getServerList(forceRefresh: Boolean): Array<String> {
        return defaultServers
    }

    @JvmStatic
    fun setCurrentServer(newServer: String?) {
        currentServer = newServer
    }

    /**
     * Rotate to a different server from the list.
     */
    @JvmStatic
    fun rotateServer() {
        val oldServer = currentServer
        val list = defaultServers
        if (list.size > 1) {
            var nextServer = list[Random().nextInt(list.size)]
            var attempts = 0
            while (nextServer == oldServer && attempts < 10) {
                nextServer = list[Random().nextInt(list.size)]
                attempts++
            }
            currentServer = nextServer
            Log.d("SRV", "Rotated from $oldServer to $currentServer")
        }
    }

    @JvmStatic
    fun getMirrorServer(): String = "radiobrowser.ounben.com"

    @JvmStatic
    fun constructEndpoint(server: String, path: String): String = "https://$server/$path"
}
