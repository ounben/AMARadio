package com.ounben.amaradio

import android.content.Context
import android.util.Log
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.FavoriteEntity
import com.ounben.amaradio.database.user.HistoryEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Reader
import java.io.Writer
import java.util.Date

open class StationSaveManager(protected val context: Context) {
    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    protected var listStations: MutableList<DataRadioStation> = ArrayList()
    protected val stationsSet = HashSet<String>()
    
    protected var stationStatusListener: StationStatusListener? = null

    protected val userDb by lazy { AMARadioUserDatabase.getDatabase(context) }
    
    private val _stationsFlow = MutableStateFlow<List<DataRadioStation>>(emptyList())
    val stationsFlow: StateFlow<List<DataRadioStation>> = _stationsFlow.asStateFlow()

    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        @Suppress("LeakingThis")
        loadFromDb()
    }

    protected open fun getSaveId(): String = "default"

    protected fun loadFromDb() {
        scope.launch {
            if (getSaveId() == "favourites") {
                userDb.favoriteDao().getAllFavoritesFlow().collect { entities ->
                    val stations = entities.map { it.toDataStation() }
                    updateInMem(stations)
                }
            } else if (getSaveId() == "history") {
                userDb.historyDao().getAllHistoryFlow().collect { entities ->
                    val stations = entities.map { it.toDataStation() }
                    updateInMem(stations)
                }
            }
        }
    }

    private fun updateInMem(stations: List<DataRadioStation>) = synchronized(this) {
        listStations.clear()
        stationsSet.clear()
        stations.forEach { 
            it.queue = this
            listStations.add(it)
            stationsSet.add(it.StationUuid)
        }
        _stationsFlow.value = listStations.toList()
        onDataChanged()
    }

    protected open fun onDataChanged() {
        // Override in subclasses
    }

    open fun add(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        addInternal(station)
    }

    fun addFront(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
        addInternal(station)
    }

    private fun addInternal(station: DataRadioStation) {
        scope.launch(Dispatchers.IO) {
            if (getSaveId() == "favourites") {
                userDb.favoriteDao().insert(station.toFavoriteEntity())
            } else if (getSaveId() == "history") {
                userDb.historyDao().addStation(station.toHistoryEntity())
            }
            withContext(Dispatchers.Main) {
                stationStatusListener?.onStationStatusChanged(station, favourite = true)
            }
        }
    }

    open fun addMultiple(stations: List<DataRadioStation>) {
        scope.launch(Dispatchers.IO) {
            stations.forEach { station ->
                if (station.queue == null) station.queue = this@StationSaveManager
                if (getSaveId() == "favourites") {
                    userDb.favoriteDao().insert(station.toFavoriteEntity())
                } else if (getSaveId() == "history") {
                    userDb.historyDao().insert(station.toHistoryEntity())
                }
            }
        }
    }

    open fun addAll(stations: List<DataRadioStation>?) {
        if (stations == null) return
        addMultiple(stations)
    }

    val first: DataRadioStation? get() = synchronized(this) { listStations.firstOrNull() }

    fun getById(id: String): DataRadioStation? = synchronized(this) { listStations.find { it.StationUuid == id } }

    fun getNextById(id: String): DataRadioStation? = synchronized(this) {
        if (listStations.isEmpty()) return null
        val idx = listStations.indexOfFirst { it.StationUuid == id }
        if (idx == -1 || idx == listStations.size - 1) return listStations[0]
        return listStations[idx + 1]
    }

    fun getPreviousById(id: String): DataRadioStation? = synchronized(this) {
        if (listStations.isEmpty()) return null
        val idx = listStations.indexOfFirst { it.StationUuid == id }
        if (idx == -1 || idx == 0) return listStations.last()
        return listStations[idx - 1]
    }

    fun remove(id: String): Int = synchronized(this) {
        val idx = listStations.indexOfFirst { it.StationUuid == id }
        if (idx != -1) {
            val station = listStations[idx]
            scope.launch(Dispatchers.IO) {
                if (getSaveId() == "favourites") {
                    userDb.favoriteDao().deleteByUuid(id)
                } else if (getSaveId() == "history") {
                    userDb.historyDao().deleteByUuid(id)
                }
                withContext(Dispatchers.Main) {
                    stationStatusListener?.onStationStatusChanged(station, favourite = false)
                }
            }
            return idx
        }
        return -1
    }

    open fun restore(station: DataRadioStation, pos: Int) {
        add(station)
    }

    fun clear() {
        scope.launch(Dispatchers.IO) {
            if (getSaveId() == "favourites") {
                val list = userDb.favoriteDao().getAllFavorites()
                list.forEach { userDb.favoriteDao().delete(it) }
            } else if (getSaveId() == "history") {
                userDb.historyDao().clearAll()
            }
        }
    }

    fun size(): Int = synchronized(this) { listStations.size }
    fun isEmpty(): Boolean = synchronized(this) { listStations.isEmpty() }
    fun has(id: String): Boolean = synchronized(this) { stationsSet.contains(id) }

    fun getList(): List<DataRadioStation> = synchronized(this) { listStations.toList() }

    open fun load() {}
    open fun save() {}

    fun exportM3U(writer: Writer): Boolean {
        val stations = getList()
        return try {
            writer.write("#EXTM3U\n")
            stations.forEach {
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
    
    private fun FavoriteEntity.toDataStation() = DataRadioStation(
        Name = name, StationUuid = stationUuid, StreamUrl = streamUrl, IconUrl = iconUrl,
        Country = country, CountryCode = countryCode, TagsAll = tags, Language = language,
        Codec = codec, Bitrate = bitrate
    )

    private fun HistoryEntity.toDataStation() = DataRadioStation(
        Name = name, StationUuid = stationUuid, StreamUrl = streamUrl, IconUrl = iconUrl,
        Country = country, CountryCode = countryCode, TagsAll = tags, Language = language,
        Codec = codec, Bitrate = bitrate
    )

    private fun DataRadioStation.toFavoriteEntity() = FavoriteEntity(
        stationUuid = StationUuid, name = Name, streamUrl = StreamUrl, iconUrl = IconUrl,
        country = Country, countryCode = CountryCode, tags = TagsAll, language = Language,
        codec = Codec, bitrate = Bitrate, addedAt = Date()
    )

    private fun DataRadioStation.toHistoryEntity() = HistoryEntity(
        stationUuid = StationUuid, name = Name, streamUrl = StreamUrl, iconUrl = IconUrl,
        country = Country, countryCode = CountryCode, tags = TagsAll, language = Language,
        codec = Codec, bitrate = Bitrate, lastPlayedAt = Date()
    )
}
