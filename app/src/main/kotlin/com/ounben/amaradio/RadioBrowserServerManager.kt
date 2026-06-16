package com.ounben.amaradio

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*

object RadioBrowserServerManager {
    private var currentServer: String? = null
    private var serverList: Array<String>? = null

    /**
     * Blocking: do dns request do get a list of all available servers
     */
    private fun doDnsServerListing(): Array<String> {
        Log.d("DNS", "doDnsServerListing()")
        val listResult = Vector<String>()
        try {
            // add all round robin servers one by one to select them separately
            val list = InetAddress.getAllByName("all.api.radio-browser.info")
            for (item in list) {
                // do not use original variable, it could fall back to "all.api.radio-browser.info"
                val currentHostAddress = item.hostAddress
                val newItem = InetAddress.getByName(currentHostAddress)
                Log.i("DNS", "Found: $newItem -> ${newItem.canonicalHostName}")
                val name = item.canonicalHostName
                if (name != "all.api.radio-browser.info" && name != currentHostAddress) {
                    Log.i("DNS", "Added entry: '$name'")
                    listResult.add(name)
                }
            }
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        }
        if (listResult.size == 0) {
            // should we inform people that their internet provider is not able to do reverse lookups? (= is shit)
            Log.w("DNS", "Fallback to de1.api.radio-browser.info because dns call did not work.")
            listResult.add("de1.api.radio-browser.info")
        }
        Log.d("DNS", "doDnsServerListing() Found servers: ${listResult.size}")
        return listResult.toTypedArray()
    }

    /**
     * Blocking: return current cached server list. Generate list if still null.
     */
    @JvmStatic
    fun getServerList(forceRefresh: Boolean): Array<String> {
        if (serverList == null || serverList!!.isEmpty() || forceRefresh) {
            serverList = doDnsServerListing()
        }
        return serverList!!
    }

    /**
     * Blocking: return current selected server. Select one, if there is no current server.
     */
    @JvmStatic
    fun getCurrentServer(): String? {
        if (currentServer == null) {
            val list = getServerList(false)
            if (list.isNotEmpty()) {
                val rand = Random()
                currentServer = list[rand.nextInt(list.size)]
                Log.d("SRV", "Selected new default server: $currentServer")
            } else {
                Log.e("SRV", "no servers found")
            }
        }
        return currentServer
    }

    /**
     * Set new server as current
     */
    @JvmStatic
    fun setCurrentServer(newServer: String?) {
        currentServer = newServer
    }

    /**
     * Construct full url from server and path
     */
    @JvmStatic
    fun constructEndpoint(server: String, path: String): String {
        return "https://$server/$path"
    }
}
