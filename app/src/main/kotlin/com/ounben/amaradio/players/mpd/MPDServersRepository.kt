package com.ounben.amaradio.players.mpd

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * MPD servers repository which is serialized into preferences.
 * It is NOT thread safe.
 * In future should be backed up by database.
 */
class MPDServersRepository(private val context: Context) {
    private val jsonConfig = Json { ignoreUnknownKeys = true }
    private var servers: MutableList<MPDServerData> = getMPDServers(context)
    
    private val _serversFlow = MutableStateFlow<List<MPDServerData>>(emptyList())
    val allServersFlow: StateFlow<List<MPDServerData>> = _serversFlow.asStateFlow()

    private var lastServerId = -1

    init {
        for (server in servers) {
            if (server.id > lastServerId) {
                lastServerId = server.id
            }
        }
        _serversFlow.value = servers
    }

    fun addServer(mpdServerData: MPDServerData) {
        mpdServerData.id = ++lastServerId
        servers.add(mpdServerData)
        saveMPDServers(servers, context)
        _serversFlow.value = ArrayList(servers)
    }

    val isEmpty: Boolean
        get() = allServersFlow.value.isEmpty()

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
            _serversFlow.value = ArrayList(servers)
        }
    }

    fun resetAllConnectionStatus() {
        for (serverData in servers) {
            serverData.connected = false
        }
        _serversFlow.value = ArrayList(servers)
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
            _serversFlow.value = ArrayList(servers)
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
            _serversFlow.value = ArrayList(servers)
        }
    }

    private fun getMPDServers(context: Context): MutableList<MPDServerData> {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val serversFromPrefs = sharedPref.getString("mpd_servers", "") ?: ""
        if (serversFromPrefs.isBlank()) return mutableListOf()
        return try {
            jsonConfig.decodeFromString<List<MPDServerData>>(serversFromPrefs).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveMPDServers(servers: List<MPDServerData>, context: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val serversJson = jsonConfig.encodeToString(servers)
        sharedPref.edit {
            putString("mpd_servers", serversJson)
        }
    }

    companion object {
        // Migration of methods to instance methods or separate object if needed
        // but here they are used locally.
    }
}
