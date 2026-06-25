package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.Reader
import java.io.Writer

open class StationSaveManager(protected val context: Context) {
    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    var listStations: MutableList<DataRadioStation> = ArrayList()
    private val stationsSet = HashSet<String>() // For O(1) lookup
    
    protected var stationStatusListener: StationStatusListener? = null

    private val _stationsFlow = MutableStateFlow<List<DataRadioStation>>(emptyList())
    val stationsFlow: StateFlow<List<DataRadioStation>> = _stationsFlow.asStateFlow()

    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val jsonConfig = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    init {
        load()
    }

    protected open fun getSaveId(): String = "default"

    open fun add(station: DataRadioStation) {
        scope.launch(Dispatchers.Default) {
            if (station.queue == null) station.queue = this@StationSaveManager
            
            withContext(Dispatchers.Main) {
                if (!stationsSet.contains(station.StationUuid)) {
                    listStations.add(station)
                    stationsSet.add(station.StationUuid)
                    save()
                    _stationsFlow.value = listStations.toList()
                    stationStatusListener?.onStationStatusChanged(station, favourite = true)
                }
            }
        }
    }

    open fun addMultiple(stations: List<DataRadioStation>) {
        scope.launch(Dispatchers.Default) {
            var changed = false
            withContext(Dispatchers.Main) {
                for (stationNew in stations) {
                    if (!stationsSet.contains(stationNew.StationUuid)) {
                        if (stationNew.queue == null) stationNew.queue = this@StationSaveManager
                        listStations.add(stationNew)
                        stationsSet.add(stationNew.StationUuid)
                        changed = true
                    }
                }
                if (changed) {
                    save()
                    _stationsFlow.value = listStations.toList()
                }
            }
        }
    }

    fun addFront(station: DataRadioStation) {
        scope.launch(Dispatchers.Default) {
            if (station.queue == null) station.queue = this@StationSaveManager
            withContext(Dispatchers.Main) {
                // For history, we allow duplicates but move to front, or just add
                listStations.add(0, station)
                stationsSet.add(station.StationUuid)
                save()
                _stationsFlow.value = listStations.toList()
                stationStatusListener?.onStationStatusChanged(station, favourite = true)
            }
        }
    }

    val first: DataRadioStation?
        get() = if (listStations.isNotEmpty()) listStations[0] else null

    fun getById(id: String): DataRadioStation? = listStations.find { it.StationUuid == id }

    fun getNextById(id: String): DataRadioStation? {
        if (listStations.isEmpty()) return null
        val idx = listStations.indexOfFirst { it.StationUuid == id }
        if (idx == -1 || idx == listStations.size - 1) return listStations[0]
        return listStations[idx + 1]
    }

    fun getPreviousById(id: String): DataRadioStation? {
        if (listStations.isEmpty()) return null
        val idx = listStations.indexOfFirst { it.StationUuid == id }
        if (idx == -1 || idx == 0) return listStations.last()
        return listStations[idx - 1]
    }

    fun remove(id: String): Int {
        val idx = listStations.indexOfFirst { it.StationUuid == id }
        if (idx != -1) {
            val station = listStations.removeAt(idx)
            stationsSet.remove(id)
            save()
            _stationsFlow.value = listStations.toList()
            stationStatusListener?.onStationStatusChanged(station, favourite = false)
            return idx
        }
        return -1
    }

    open fun restore(station: DataRadioStation, pos: Int) {
        station.queue = this
        listStations.add(pos.coerceIn(0, listStations.size), station)
        stationsSet.add(station.StationUuid)
        save()
        _stationsFlow.value = listStations.toList()
        stationStatusListener?.onStationStatusChanged(station, favourite = true)
    }

    fun clear() {
        val oldStations = ArrayList(listStations)
        listStations.clear()
        stationsSet.clear()
        save()
        _stationsFlow.value = emptyList()
        oldStations.forEach { stationStatusListener?.onStationStatusChanged(it, favourite = false) }
    }

    fun size(): Int = listStations.size
    fun isEmpty(): Boolean = listStations.isEmpty()
    fun has(id: String): Boolean = stationsSet.contains(id)

    private fun hasInvalidUuids(): Boolean = listStations.any { !it.hasValidUuid() }

    fun getList(): List<DataRadioStation> = java.util.Collections.unmodifiableList(listStations)

    open fun load() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val str = sharedPref.getString(getSaveId(), null) ?: return

        scope.launch {
            val arr = withContext(Dispatchers.Default) {
                DataRadioStation.DecodeJson(str)
            }
            
            if (arr != null) {
                withContext(Dispatchers.Main) {
                    val uniqueStations = arr.distinctBy { it.StationUuid }
                    listStations.clear()
                    stationsSet.clear()
                    for (station in uniqueStations) {
                        station.queue = this@StationSaveManager
                        listStations.add(station)
                        stationsSet.add(station.StationUuid)
                    }
                    _stationsFlow.value = listStations.toList()
                }
            }
        }
    }

    open fun save() {
        val stationsCopy = ArrayList(listStations)
        scope.launch {
            val str = withContext(Dispatchers.Default) {
                jsonConfig.encodeToString(stationsCopy)
            }
            withContext(Dispatchers.IO) {
                PreferenceManager.getDefaultSharedPreferences(context).edit {
                    putString(getSaveId(), str)
                }
            }
        }
    }

    fun exportM3U(writer: Writer): Boolean {
        return try {
            writer.write("#EXTM3U\n")
            listStations.forEach {
                writer.write("#RADIOBROWSERUUID:${it.StationUuid}\n")
                writer.write("#EXTINF:-1,${it.Name}\n")
                writer.write("${it.StreamUrl}\n\n")
            }
            writer.flush()
            true
        } catch (e: Exception) {
            Log.e("SAVE", "M3U Export failed: $e")
            false
        }
    }

    suspend fun importM3U(reader: Reader): List<DataRadioStation>? = withContext(Dispatchers.IO) {
        try {
            val listUuids = ArrayList<String>()
            reader.readLines().forEach { line ->
                if (line.startsWith("#RADIOBROWSERUUID:")) {
                    val uuid = line.substringAfter(":").trim()
                    if (uuid.isNotEmpty()) listUuids.add(uuid)
                }
            }
            if (listUuids.isEmpty()) return@withContext emptyList()
            val app = context.applicationContext as AMARadioApp
            com.ounben.amaradio.Utils.getStationsByUuid(app.httpClient, context, listUuids)
        } catch (e: Exception) {
            Log.e("LOAD", "M3U Import failed: $e")
            null
        }
    }
}
