package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.BuildConfig
import org.json.JSONArray
import java.io.*
import java.util.*
import org.apache.commons.text.similarity.CosineSimilarity
import kotlinx.coroutines.*

open class StationSaveManager(protected val context: Context) : Observable() {
    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    var listStations: MutableList<DataRadioStation> = ArrayList()
    protected var stationStatusListener: StationStatusListener? = null

    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        setChanged()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, true)
    }

    fun addMultiple(stations: List<DataRadioStation>) {
        for (stationNew in stations) {
            listStations.add(stationNew)
        }
        save()
        setChanged()
        notifyObservers()
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
        setChanged()
        notifyObservers()
    }

    fun addFront(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        listStations.add(0, station)
        save()
        setChanged()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, true)
    }

    fun addAll(stations: List<DataRadioStation>?) {
        if (stations == null) return
        for (station in stations) {
            station.queue = this
        }
        listStations.addAll(stations)
    }

    val last: DataRadioStation?
        get() = if (listStations.isNotEmpty()) listStations[listStations.size - 1] else null

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
        for (i in 0 until listStations.size - 1) {
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

    fun move(fromPos: Int, toPos: Int) {
        moveWithoutNotify(fromPos, toPos)
        setChanged()
        notifyObservers()
    }

    fun getBestNameMatch(query: String): DataRadioStation? {
        var bestStation: DataRadioStation? = null
        val upperQuery = query.uppercase(Locale.ROOT)
        var maxSimilarity = 0.0
        val simMeasure = CosineSimilarity()
        
        // Helper to convert string to Map<CharSequence, Int> for CosineSimilarity
        fun String.getTermFrequencies(): Map<CharSequence, Int> {
            return this.toCharArray().groupBy { it.toString() as CharSequence }.mapValues { it.value.size }
        }

        val queryTerms = upperQuery.getTermFrequencies()

        for (station in listStations) {
            val stationName = station.Name.uppercase(Locale.ROOT)
            val similarity = simMeasure.cosineSimilarity(stationName.getTermFrequencies(), queryTerms)
            if (similarity > maxSimilarity) {
                bestStation = station
                maxSimilarity = similarity
            }
        }
        return bestStation
    }

    fun remove(id: String): Int {
        for (i in listStations.indices) {
            val station = listStations[i]
            if (station.StationUuid == id) {
                listStations.removeAt(i)
                save()
                setChanged()
                notifyObservers()
                stationStatusListener?.onStationStatusChanged(station, false)
                return i
            }
        }
        return -1
    }

    open fun restore(station: DataRadioStation, pos: Int) {
        station.queue = this
        listStations.add(pos, station)
        save()
        setChanged()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, false)
    }

    fun clear() {
        val oldStation = listStations
        listStations = ArrayList()
        save()
        setChanged()
        notifyObservers()
        if (stationStatusListener != null) {
            for (station in oldStation) {
                stationStatusListener!!.onStationStatusChanged(station, false)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun hasChanged(): Boolean {
        return true
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
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))

        scope.launch {
            val savedStations = ArrayList(listStations)
            val stationsToRemove = withContext(Dispatchers.IO) {
                val toRemove = ArrayList<DataRadioStation>()
                for (station in savedStations) {
                    if (!station.refresh(httpClient, context) && !station.hasValidUuid() && station.RefreshRetryCount > DataRadioStation.MAX_REFRESH_RETRIES) {
                        toRemove.add(station)
                    }
                }
                toRemove
            }

            listStations.removeAll(stationsToRemove)
            save()
            setChanged()
            notifyObservers()
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
        }
    }

    open fun load() {
        listStations.clear()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val str = sharedPref.getString(getSaveId(), null)
        if (str != null) {
            val arr = DataRadioStation.DecodeJson(str)
            if (arr != null) {
                for (station in arr) {
                    station.queue = this
                }
                listStations.addAll(arr)
                if (hasInvalidUuids() && Utils.hasAnyConnection(context)) {
                    refreshStationsFromServer()
                }
            }
        } else {
            Log.w("SAVE", "load() no stations to load")
        }
    }

    open fun save() {
        val arr = JSONArray()
        for (station in listStations) {
            arr.put(station.toJson())
        }
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val str = arr.toString()
        if (BuildConfig.DEBUG) {
            Log.d("SAVE", "wrote: $str")
        }
        sharedPref.edit().putString(getSaveId(), str).apply()
    }

    /**
     * Schreibt die aktuelle Liste im M3U-Format in den Writer.
     * Schließt den Writer NICHT (überlassen wir dem Aufrufer/use-Block).
     */
    fun exportM3U(writer: Writer): Boolean {
        return try {
            writer.write("#EXTM3U\n")
            for (station in listStations) {
                writer.write(M3U_PREFIX + station.StationUuid + "\n")
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
                if (line?.startsWith(M3U_PREFIX) == true) {
                    val uuid = line!!.substring(M3U_PREFIX.length).trim()
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

    protected val M3U_PREFIX = "#RADIOBROWSERUUID:"
}
