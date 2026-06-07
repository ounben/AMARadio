package net.ounben.AMARadio

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.BuildConfig
import org.json.JSONArray
import java.io.*
import java.util.*
import info.debatty.java.stringsimilarity.Cosine
import kotlinx.coroutines.*

open class StationSaveManager(protected val context: Context) : Observable() {
    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    var listStations: MutableList<DataRadioStation> = ArrayList()
    protected var stationStatusListener: StationStatusListener? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        Load()
    }

    protected open fun getSaveId(): String {
        return "default"
    }

    open fun add(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        listStations.add(station)
        Save()
        setChanged()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, true)
    }

    fun addMultiple(stations: List<DataRadioStation>) {
        for (station_new in stations) {
            listStations.add(station_new)
        }
        Save()
        setChanged()
        notifyObservers()
    }

    fun replaceList(stations_new: List<DataRadioStation>) {
        for (station_new in stations_new) {
            for (i in listStations.indices) {
                if (listStations[i].StationUuid == station_new.StationUuid) {
                    listStations[i] = station_new
                    break
                }
            }
        }
        Save()
        setChanged()
        notifyObservers()
    }

    fun addFront(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        listStations.add(0, station)
        Save()
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
        Collections.rotate(listStations.subList(Math.min(fromPos, toPos), Math.max(fromPos, toPos) + 1), Integer.signum(fromPos - toPos))
    }

    fun move(fromPos: Int, toPos: Int) {
        moveWithoutNotify(fromPos, toPos)
        setChanged()
        notifyObservers()
    }

    fun getBestNameMatch(query: String): DataRadioStation? {
        var bestStation: DataRadioStation? = null
        val upperQuery = query.uppercase(Locale.ROOT)
        var smallesDistance = Double.MAX_VALUE
        val distMeasure = Cosine()
        for (station in listStations) {
            val distance = distMeasure.distance(station.Name.uppercase(Locale.ROOT), upperQuery)
            if (distance < smallesDistance) {
                bestStation = station
                smallesDistance = distance
            }
        }
        return bestStation
    }

    fun remove(id: String): Int {
        for (i in listStations.indices) {
            val station = listStations[i]
            if (station.StationUuid == id) {
                listStations.removeAt(i)
                Save()
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
        Save()
        setChanged()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, false)
    }

    fun clear() {
        val oldStation = listStations
        listStations = ArrayList()
        Save()
        setChanged()
        notifyObservers()
        if (stationStatusListener != null) {
            for (station in oldStation) {
                stationStatusListener!!.onStationStatusChanged(station, false)
            }
        }
    }

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
        val AMARadioApp = context.applicationContext as AMARadioApp
        val httpClient = AMARadioApp.httpClient
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
            Save()
            setChanged()
            notifyObservers()
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
        }
    }

    open fun Load() {
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
            Log.w("SAVE", "Load() no stations to load")
        }
    }

    open fun Save() {
        val arr = JSONArray()
        for (station in listStations) {
            arr.put(station.toJson())
        }
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPref.edit()
        val str = arr.toString()
        if (BuildConfig.DEBUG) {
            Log.d("SAVE", "wrote: $str")
        }
        editor.putString(getSaveId(), str)
        editor.apply()
    }

    fun SaveM3U(filePath: String, fileName: String) {
        Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_now, filePath, fileName), Toast.LENGTH_LONG).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                SaveM3UInternal(filePath, fileName)
            }
            if (result) {
                Log.i("SAVE", "OK")
                Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_ok, filePath, fileName), Toast.LENGTH_LONG).show()
            } else {
                Log.i("SAVE", "NOK")
                Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_nok, filePath, fileName), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun SaveM3USimple(filePath: String, fileName: String) {
        SaveM3U(filePath, fileName)
    }

    fun LoadM3U(filePath: String, fileName: String) {
        Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_now, filePath, fileName), Toast.LENGTH_LONG).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LoadM3UInternal(filePath, fileName)
            }
            if (result != null) {
                Log.i("LOAD", "Loaded " + result.size + "stations")
                addMultiple(result)
                Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_ok, result.size, filePath, fileName), Toast.LENGTH_LONG).show()
            } else {
                Log.e("LOAD", "Load failed")
                Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_nok, filePath, fileName), Toast.LENGTH_LONG).show()
            }
            setChanged()
            notifyObservers()
        }
    }

    fun LoadM3USimple(reader: Reader) {
        Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_now, "", ""), Toast.LENGTH_LONG).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LoadM3UReader(reader)
            }
            if (result != null) {
                Log.i("LOAD", "Loaded " + result.size + "stations")
                addMultiple(result)
                Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_ok, result.size, "", ""), Toast.LENGTH_LONG).show()
            } else {
                Log.e("LOAD", "Load failed")
                Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_nok, "", ""), Toast.LENGTH_LONG).show()
            }
            setChanged()
            notifyObservers()
        }
    }

    protected val M3U_PREFIX = "#RADIOBROWSERUUID:"

    private fun SaveM3UInternal(filePath: String, fileName: String): Boolean {
        try {
            val f = File(filePath, fileName)
            val bw = BufferedWriter(FileWriter(f, false))
            val r = SaveM3UWriter(bw)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))))
            } else {
                MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null)
            }
            return r
        } catch (e: Exception) {
            Log.e("Exception", "File write failed: $e")
            return false
        }
    }

    fun SaveM3UWriter(bw: Writer): Boolean {
        try {
            bw.write("#EXTM3U\n")
            for (station in listStations) {
                bw.write(M3U_PREFIX + station.StationUuid + "\n")
                bw.write("#EXTINF:-1," + station.Name + "\n")
                bw.write(station.StreamUrl + "\n\n")
            }
            bw.flush()
            bw.close()
            return true
        } catch (e: Exception) {
            Log.e("Exception", "File write failed: $e")
            return false
        }
    }

    private fun LoadM3UInternal(filePath: String, fileName: String): List<DataRadioStation>? {
        return try {
            val f = File(filePath, fileName)
            val fr = FileReader(f)
            LoadM3UReader(fr)
        } catch (e: Exception) {
            Log.e("LOAD", "File read failed: $e")
            null
        }
    }

    fun LoadM3UReader(reader: Reader): List<DataRadioStation>? {
        try {
            var line: String?
            val AMARadioApp = context.applicationContext as AMARadioApp
            val httpClient = AMARadioApp.httpClient
            val listUuids = ArrayList<String>()

            val br = BufferedReader(reader)
            while (br.readLine().also { line = it } != null) {
                Log.v("LOAD", "line: $line")
                if (line!!.startsWith(M3U_PREFIX)) {
                    try {
                        val uuid = line!!.substring(M3U_PREFIX.length).trim { it <= ' ' }
                        listUuids.add(uuid)
                    } catch (e: Exception) {
                        Log.e("LOAD", e.toString())
                    }
                }
            }
            br.close()

            val listStationsNew = Utils.getStationsByUuid(httpClient, context, listUuids) ?: return null

            // sort list to have the same order as the initial save file
            val listStationsSorted = ArrayList<DataRadioStation>()
            for (uuid in listUuids) {
                for (s in listStationsNew) {
                    if (uuid == s.StationUuid) {
                        listStationsSorted.add(s)
                        break
                    }
                }
            }
            return listStationsSorted
        } catch (e: Exception) {
            Log.e("LOAD", "File read failed: $e")
            return null
        }
    }

    companion object {
        @JvmStatic
        val saveDir: String
            get() {
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString() + ""
                val folder = File(path)
                if (!folder.exists()) {
                    if (!folder.mkdirs()) {
                        Log.e("SAVE", "could not create dir:$path")
                    }
                }
                return path
            }
    }
}
