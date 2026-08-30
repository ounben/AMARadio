package com.ounben.amaradio

import android.content.Context
import android.util.Log
import com.ounben.amaradio.database.toCustomEntity
import com.ounben.amaradio.database.toDataStation
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
            } else if (getSaveId() == "custom") {
                userDb.customStationDao().getAllCustomStationsFlow().collect { entities ->
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
                val maxOrder = userDb.favoriteDao().getMaxOrder() ?: -1
                userDb.favoriteDao().insert(station.toFavoriteEntity(maxOrder + 1))
            } else if (getSaveId() == "history") {
                userDb.historyDao().addStation(station.toHistoryEntity())
            } else if (getSaveId() == "custom") {
                val maxOrder = userDb.customStationDao().getMaxOrder() ?: -1
                userDb.customStationDao().insert(station.toCustomEntity(maxOrder + 1))
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
                    val maxOrder = userDb.favoriteDao().getMaxOrder() ?: -1
                    userDb.favoriteDao().insert(station.toFavoriteEntity(maxOrder + 1))
                } else if (getSaveId() == "history") {
                    userDb.historyDao().insert(station.toHistoryEntity())
                } else if (getSaveId() == "custom") {
                    val maxOrder = userDb.customStationDao().getMaxOrder() ?: -1
                    userDb.customStationDao().insert(station.toCustomEntity(maxOrder + 1))
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

    open fun remove(id: String): Int = synchronized(this) {
        val idx = listStations.indexOfFirst { it.StationUuid == id }
        if (idx != -1) {
            val station = listStations[idx]
            scope.launch(Dispatchers.IO) {
                if (getSaveId() == "favourites") {
                    userDb.favoriteDao().deleteByUuid(id)
                } else if (getSaveId() == "history") {
                    userDb.historyDao().deleteByUuid(id)
                } else if (getSaveId() == "custom") {
                    userDb.customStationDao().deleteByUuid(id)
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

    open fun reorder(fromIndex: Int, toIndex: Int) {
        // Implement in subclasses
    }

    open fun persistOrder(stations: List<DataRadioStation>) {
        // Implement in subclasses
    }

    fun clear() {
        scope.launch(Dispatchers.IO) {
            if (getSaveId() == "favourites") {
                val list = userDb.favoriteDao().getAllFavorites()
                list.forEach { userDb.favoriteDao().delete(it) }
            } else if (getSaveId() == "history") {
                userDb.historyDao().clearAll()
            } else if (getSaveId() == "custom") {
                userDb.customStationDao().deleteAll()
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
        Name = Name, StationUuid = StationUuid, StreamUrl = Url, IconUrl = Favicon,
        HomePageUrl = Homepage, Country = Country, CountryCode = CountryCode, 
        TagsAll = Tags, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, State = Subcountry, ClickCount = clickcount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime
    )

    private fun HistoryEntity.toDataStation() = DataRadioStation(
        Name = Name, StationUuid = StationUuid, StreamUrl = Url, IconUrl = Favicon,
        HomePageUrl = Homepage, Country = Country, CountryCode = CountryCode, 
        TagsAll = Tags, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, State = Subcountry, ClickCount = clickcount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime
    )

    private fun DataRadioStation.toFavoriteEntity(order: Int = 0) = FavoriteEntity(
        StationUuid = StationUuid, Name = Name, Url = StreamUrl, Favicon = IconUrl,
        Homepage = HomePageUrl, Country = Country, CountryCode = CountryCode, 
        Tags = TagsAll, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, Subcountry = State, clickcount = ClickCount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime,
        addedAt = Date(), displayOrder = order
    )

    private fun DataRadioStation.toHistoryEntity() = HistoryEntity(
        StationUuid = StationUuid, Name = Name, Url = StreamUrl, Favicon = IconUrl,
        Homepage = HomePageUrl, Country = Country, CountryCode = CountryCode, 
        Tags = TagsAll, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, Subcountry = State, clickcount = ClickCount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime,
        lastPlayedAt = Date()
    )
}
