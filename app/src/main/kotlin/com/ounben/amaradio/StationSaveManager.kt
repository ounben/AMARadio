package com.ounben.amaradio

import android.content.Context
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
import java.io.Reader
import java.io.Writer

open class StationSaveManager(protected val context: Context) {
    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    protected var listStations: MutableList<DataRadioStation> = ArrayList()
    protected val stationsSet = HashSet<String>()
    
    protected var stationStatusListener: StationStatusListener? = null

    private val _stationsFlow = MutableStateFlow<List<DataRadioStation>>(emptyList())
    val stationsFlow: StateFlow<List<DataRadioStation>> = _stationsFlow.asStateFlow()

    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val jsonConfig = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    init {
        @Suppress("LeakingThis")
        load()
    }

    protected open fun getSaveId(): String = "default"

    open fun add(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        if (!stationsSet.contains(station.StationUuid)) {
            listStations.add(station)
            stationsSet.add(station.StationUuid)
            save()
            _stationsFlow.value = listStations.toList()
            stationStatusListener?.onStationStatusChanged(station, favourite = true)
        }
    }

    open fun addMultiple(stations: List<DataRadioStation>) {
        var changed = false
        for (stationNew in stations) {
            if (!stationsSet.contains(stationNew.StationUuid)) {
                if (stationNew.queue == null) stationNew.queue = this
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

    open fun addAll(stations: List<DataRadioStation>?) {
        if (stations == null) return
        var changed = false
        for (station in stations) {
            if (!stationsSet.contains(station.StationUuid)) {
                station.queue = this
                listStations.add(station)
                stationsSet.add(station.StationUuid)
                changed = true
            }
        }
        if (changed) {
            _stationsFlow.value = listStations.toList()
        }
    }

    fun addFront(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        listStations.removeAll { it.StationUuid == station.StationUuid }
        listStations.add(0, station)
        stationsSet.add(station.StationUuid)
        save()
        _stationsFlow.value = listStations.toList()
        stationStatusListener?.onStationStatusChanged(station, favourite = true)
    }

    val first: DataRadioStation? get() = listStations.firstOrNull()

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
        val safePos = pos.coerceIn(0, listStations.size)
        listStations.add(safePos, station)
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

    fun getList(): List<DataRadioStation> = java.util.Collections.unmodifiableList(listStations)

    open fun load() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val str = sharedPref.getString(getSaveId(), null)
        if (str != null) {
            try {
                val arr = DataRadioStation.DecodeJson(str)
                if (arr != null) {
                    listStations.clear()
                    stationsSet.clear()
                    arr.distinctBy { it.StationUuid }.forEach {
                        it.queue = this
                        listStations.add(it)
                        stationsSet.add(it.StationUuid)
                    }
                }
            } catch (e: Exception) {
                Log.e("SAVE", "Error loading stations", e)
            }
        }
        _stationsFlow.value = listStations.toList()
    }

    open fun save() {
        val stationsCopy = ArrayList(listStations)
        scope.launch(Dispatchers.IO) {
            try {
                val str = jsonConfig.encodeToString(stationsCopy)
                PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
                    putString(getSaveId(), str)
                }
            } catch (e: Exception) {
                Log.e("SAVE", "Error saving stations", e)
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
                    val uuid = line.substringAfter("#RADIOBROWSERUUID:").trim()
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
