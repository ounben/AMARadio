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
import java.util.Collections

open class StationSaveManager(protected val context: Context) {
    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    var listStations: MutableList<DataRadioStation> = ArrayList()
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

    protected open fun getSaveId(): String {
        return "default"
    }

    open fun add(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        listStations.add(station)
        save()
        _stationsFlow.value = listStations.toList()
        stationStatusListener?.onStationStatusChanged(station, favourite = true)
    }

    open fun addMultiple(stations: List<DataRadioStation>) {
        var changed = false
        for (stationNew in stations) {
            if (!has(stationNew.StationUuid)) {
                if (stationNew.queue == null) stationNew.queue = this
                listStations.add(stationNew)
                changed = true
            }
        }
        if (changed) {
            save()
            _stationsFlow.value = listStations.toList()
        }
    }

    fun replaceList(stationsNew: List<DataRadioStation>) {
        for (stationNew in stationsNew) {
            for (i in listStations.indices) {
                if (listStations[i].StationUuid == stationNew.StationUuid) {
                    listStations[i] = stationNew
                    break
                }
            }
        }
        save()
        _stationsFlow.value = listStations.toList()
    }

    fun addFront(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        listStations.add(0, station)
        save()
        _stationsFlow.value = listStations.toList()
        stationStatusListener?.onStationStatusChanged(station, favourite = true)
    }

    fun addAll(stations: List<DataRadioStation>?) {
        if (stations == null) return
        for (station in stations) {
            station.queue = this
        }
        listStations.addAll(stations)
        _stationsFlow.value = listStations.toList()
    }

    val first: DataRadioStation?
        get() = if (listStations.isNotEmpty()) listStations[0] else null

    fun getById(id: String): DataRadioStation? {
        for (station in listStations) {
            if (id == station.StationUuid) {
                return station
            }
        }
        return null
    }

    fun getNextById(id: String): DataRadioStation? {
        if (listStations.isEmpty()) return null
        for (i in 0 until (listStations.size - 1)) {
            if (listStations[i].StationUuid == id) {
                return listStations[i + 1]
            }
        }
        return listStations[0]
    }

    fun getPreviousById(id: String): DataRadioStation? {
        if (listStations.isEmpty()) return null
        for (i in 1 until listStations.size) {
            if (listStations[i].StationUuid == id) {
                return listStations[i - 1]
            }
        }
        return listStations[listStations.size - 1]
    }

    fun moveWithoutNotify(fromPos: Int, toPos: Int) {
        Collections.rotate(listStations.subList(minOf(fromPos, toPos), maxOf(fromPos, toPos) + 1), Integer.signum(fromPos - toPos))
    }

    fun remove(id: String): Int {
        for (i in listStations.indices) {
            val station = listStations[i]
            if (station.StationUuid == id) {
                listStations.removeAt(i)
                save()
                _stationsFlow.value = listStations.toList()
                stationStatusListener?.onStationStatusChanged(station, favourite = false)
                return i
            }
        }
        return -1
    }

    open fun restore(station: DataRadioStation, pos: Int) {
        station.queue = this
        listStations.add(pos, station)
        save()
        _stationsFlow.value = listStations.toList()
        stationStatusListener?.onStationStatusChanged(station, favourite = false)
    }

    fun clear() {
        val oldStation = listStations
        listStations = ArrayList()
        save()
        _stationsFlow.value = listStations.toList()
        if (stationStatusListener != null) {
            for (station in oldStation) {
                stationStatusListener!!.onStationStatusChanged(station, favourite = false)
            }
        }
    }

    fun size(): Int {
        return listStations.size
    }

    fun isEmpty(): Boolean {
        return listStations.size == 0
    }

    fun has(id: String): Boolean {
        val station = getById(id)
        return station != null
    }

    private fun hasInvalidUuids(): Boolean {
        for (station in listStations) {
            if (!station.hasValidUuid()) {
                return true
            }
        }
        return false
    }

    fun getList(): List<DataRadioStation> {
        return Collections.unmodifiableList(listStations)
    }

    private fun refreshStationsFromServer() {
        val app = context.applicationContext as AMARadioApp
        val httpClient = app.httpClient
        AppEventManager.sendEvent(Intent(ActivityMain.ACTION_SHOW_LOADING))

        scope.launch {
            val savedStations = ArrayList(listStations)
            val stationsToRemove = withContext(Dispatchers.IO) {
                val toRemove = ArrayList<DataRadioStation>()
                for (station in savedStations) {
                    if (!station.refresh(httpClient, context) && (!station.hasValidUuid()) && (station.RefreshRetryCount > DataRadioStation.MAX_REFRESH_RETRIES)) {
                        toRemove.add(station)
                    }
                }
                toRemove
            }

            listStations.removeAll(stationsToRemove)
            save()
            _stationsFlow.value = listStations.toList()
            AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))
        }
    }

    open fun load() {
        listStations.clear()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val str = sharedPref.getString(getSaveId(), null)
        if (str != null) {
            val arr = DataRadioStation.DecodeJson(str)
            if (arr != null) {
                val uniqueStations = arr.distinctBy { it.StationUuid }
                for (station in uniqueStations) {
                    station.queue = this
                }
                listStations.addAll(uniqueStations)
                
                if (uniqueStations.size < arr.size) {
                    Log.w("SAVE", "Cleaned up ${arr.size - uniqueStations.size} duplicates in ${getSaveId()}")
                    save() // Persistent fix
                }

                if (hasInvalidUuids() && Utils.hasAnyConnection(context)) {
                    refreshStationsFromServer()
                }
            }
        } else {
            Log.w("SAVE", "load() no stations to load")
        }
        _stationsFlow.value = listStations.toList()
    }

    open fun save() {
        val stationsCopy = ArrayList(listStations)
        scope.launch {
            val str = withContext(Dispatchers.Default) {
                jsonConfig.encodeToString(stationsCopy)
            }
            
            withContext(Dispatchers.IO) {
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
                sharedPref.edit {
                    putString(getSaveId(), str)
                }
            }
            
            if (Utils.isDebug) {
                Log.d("SAVE", "Saved stations for ${getSaveId()}")
            }
            _stationsFlow.value = stationsCopy.toList()
        }
    }

    /**
     * Schreibt die aktuelle Liste im M3U-Format in den Writer.
     * Schließt den Writer NICHT (überlassen wir dem Aufrufer/use-Block).
     */
    fun exportM3U(writer: Writer): Boolean {
        return try {
            writer.write("#EXTM3U\n")
            for (station in listStations) {
                writer.write(m3uPrefix + station.StationUuid + "\n")
                writer.write("#EXTINF:-1," + station.Name + "\n")
                writer.write(station.StreamUrl + "\n\n")
            }
            writer.flush()
            true
        } catch (e: Exception) {
            Log.e("SAVE", "M3U Export failed: $e")
            false
        }
    }

    /**
     * Liest Stationen aus einem Reader (M3U-Format) ein.
     * Synchroner Aufruf, muss in einem Hintergrund-Thread erfolgen.
     */
    fun importM3U(reader: Reader): List<DataRadioStation>? {
        try {
            val listUuids = ArrayList<String>()
            val br = BufferedReader(reader)
            var line: String?
            while (br.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.startsWith(m3uPrefix)) {
                    val uuid = currentLine.substring(m3uPrefix.length).trim()
                    if (uuid.isNotEmpty()) {
                        listUuids.add(uuid)
                    }
                }
            }
            
            if (listUuids.isEmpty()) return emptyList()

            val app = context.applicationContext as AMARadioApp
            val listStationsNew = Utils.getStationsByUuid(app.httpClient, context, listUuids) ?: return null

            // Sortierung beibehalten
            val listStationsSorted = ArrayList<DataRadioStation>()
            for (uuid in listUuids) {
                listStationsNew.find { it.StationUuid == uuid }?.let { 
                    listStationsSorted.add(it)
                }
            }
            return listStationsSorted
        } catch (e: Exception) {
            Log.e("LOAD", "M3U Import failed: $e")
            return null
        }
    }

    private val m3uPrefix = "#RADIOBROWSERUUID:"
}
