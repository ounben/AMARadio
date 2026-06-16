package com.ounben.amaradio.players.mpd

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * MPD servers repository which is serialized into preferences.
 * It is NOT thread safe.
 * In future should be backed up by database.
 */
class MPDServersRepository(private val context: Context) {
    private var servers: MutableList<MPDServerData> = getMPDServers(context)
    private val serversLiveData = MutableLiveData<List<MPDServerData>>()
    private var lastServerId = -1

    init {
        for (server in servers) {
            if (server.id > lastServerId) {
                lastServerId = server.id
            }
        }
        serversLiveData.value = servers
    }

    val allServers: LiveData<List<MPDServerData>>
        get() = serversLiveData

    fun addServer(mpdServerData: MPDServerData) {
        mpdServerData.id = ++lastServerId
        servers.add(mpdServerData)
        saveMPDServers(servers, context)
        serversLiveData.postValue(servers)
    }

    val isEmpty: Boolean
        get() = serversLiveData.value?.isEmpty() ?: true

    fun removeServer(mpdServerData: MPDServerData) {
        var changed = false
        val iterator = servers.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == mpdServerData.id) {
                iterator.remove()
                changed = true
                break
            }
        }
        if (changed) {
            saveMPDServers(servers, context)
            serversLiveData.postValue(servers)
        }
    }

    fun resetAllConnectionStatus() {
        for (serverData in servers) {
            serverData.connected = false
        }
        serversLiveData.postValue(serversLiveData.value)
    }

    fun updatePersistentData(mpdServerData: MPDServerData) {
        var changed = false
        for (i in servers.indices) {
            val data = servers[i]
            if (data.id == mpdServerData.id && !data.contentEquals(mpdServerData)) {
                servers[i] = mpdServerData
                changed = true
                break
            }
        }
        if (changed) {
            saveMPDServers(servers, context)
            serversLiveData.postValue(serversLiveData.value)
        }
    }

    fun updateRuntimeData(mpdServerData: MPDServerData) {
        var changed = false
        for (i in servers.indices) {
            val data = servers[i]
            if (data.id == mpdServerData.id && !data.contentEquals(mpdServerData)) {
                servers[i] = mpdServerData
                changed = true
                break
            }
        }
        if (changed) {
            serversLiveData.postValue(serversLiveData.value)
        }
    }

    companion object {
        private fun getMPDServers(context: Context): MutableList<MPDServerData> {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            val serversFromPrefs = sharedPref.getString("mpd_servers", "")
            val gson = Gson()
            val type = object : TypeToken<ArrayList<MPDServerData>>() {}.type
            val serversList: MutableList<MPDServerData>? = gson.fromJson(serversFromPrefs, type)
            return serversList ?: mutableListOf()
        }

        private fun saveMPDServers(servers: List<MPDServerData>, context: Context) {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = sharedPref.edit()
            val gson = Gson()
            val serversJson = gson.toJson(servers)
            editor.putString("mpd_servers", serversJson)
            editor.apply()
        }
    }
}
