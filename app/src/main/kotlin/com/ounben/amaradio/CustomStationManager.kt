package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.database.toCustomEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class CustomStationManager(ctx: Context) : StationSaveManager(ctx) {

    override fun getSaveId(): String = "custom"

    override fun remove(id: String): Int {
        val idx = super.remove(id)
        if (idx != -1) {
            scope.launch(Dispatchers.IO) {
                userDb.favoriteDao().deleteByUuid(id)
                userDb.historyDao().deleteByUuid(id)
            }
        }
        return idx
    }

    fun updateStation(station: DataRadioStation) {
        scope.launch(Dispatchers.IO) {
            val idx = listStations.indexOfFirst { it.StationUuid == station.StationUuid }
            if (idx != -1) {
                userDb.customStationDao().update(station.toCustomEntity(idx))
            }
            
            // Sync with favorites
            userDb.favoriteDao().getByUuid(station.StationUuid)?.let { fav ->
                userDb.favoriteDao().update(station.toFavoriteEntitySync(fav.displayOrder, fav.addedAt))
            }
            
            // Sync with history (Room REPLACE strategy on insert or update by UUID if applicable)
            userDb.historyDao().getByUuid(station.StationUuid)?.let { hist ->
                userDb.historyDao().insert(station.toHistoryEntitySync(hist.lastPlayedAt))
            }
        }
    }

    private fun DataRadioStation.toFavoriteEntitySync(order: Int, addedAt: java.util.Date) = com.ounben.amaradio.database.user.FavoriteEntity(
        StationUuid = StationUuid, Name = Name, Url = StreamUrl, Favicon = IconUrl,
        Homepage = HomePageUrl, Country = Country, CountryCode = CountryCode, 
        Tags = TagsAll, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, Subcountry = State, clickcount = ClickCount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime,
        addedAt = addedAt, displayOrder = order
    )

    private fun DataRadioStation.toHistoryEntitySync(lastPlayedAt: java.util.Date) = com.ounben.amaradio.database.user.HistoryEntity(
        StationUuid = StationUuid, Name = Name, Url = StreamUrl, Favicon = IconUrl,
        Homepage = HomePageUrl, Country = Country, CountryCode = CountryCode, 
        Tags = TagsAll, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, Subcountry = State, clickcount = ClickCount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime,
        lastPlayedAt = lastPlayedAt
    )

    override fun reorder(fromIndex: Int, toIndex: Int) {
        scope.launch(Dispatchers.IO) {
            val newList = listStations.toMutableList()
            if (fromIndex !in newList.indices || toIndex !in newList.indices) return@launch
            
            val item = newList.removeAt(fromIndex)
            newList.add(toIndex, item)
            
            persistOrder(newList)
        }
    }

    override fun persistOrder(stations: List<DataRadioStation>) {
        scope.launch(Dispatchers.IO) {
            val entities = stations.mapIndexed { index, station ->
                station.toCustomEntity(index)
            }
            userDb.customStationDao().updateAll(entities)
        }
    }

    companion object {
        fun generateUuidFromUrl(url: String): String {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return "custom_$hash"
        }
    }
}
