package com.ounben.amaradio

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import com.ounben.amaradio.database.user.FavoriteEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.min

class FavouriteManager(ctx: Context) : StationSaveManager(ctx) {

    override fun getSaveId(): String {
        return "favourites"
    }

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
                station.toFavoriteEntityInternal(index)
            }
            userDb.favoriteDao().updateAll(entities)
        }
    }

    private fun DataRadioStation.toFavoriteEntityInternal(order: Int) = FavoriteEntity(
        StationUuid = StationUuid, Name = Name, Url = StreamUrl, Favicon = IconUrl,
        Homepage = HomePageUrl, Country = Country, CountryCode = CountryCode, 
        Tags = TagsAll, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, Subcountry = State, clickcount = ClickCount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime,
        addedAt = Date(), displayOrder = order
    )

    init {
        stationStatusListener = object : StationStatusListener {
            override fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean) {
                val local = Intent()
                local.action = DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED
                local.putExtra(DataRadioStation.RADIO_STATION_UUID, station.StationUuid)
                AppEventManager.sendEvent(local)
            }
        }
    }

    override fun add(station: DataRadioStation) {
        if (!has(station.StationUuid)) {
            super.add(station)
            (context.applicationContext as? AMARadioApp)?.reviewManager?.incrementActionCount()
        }
    }

    override fun onDataChanged() {
        updateShortcuts()
    }

    fun updateShortcuts() {
        if (Build.VERSION.SDK_INT >= 25 && !Utils.isTesting()) {
            val number = min(listStations.size, ActivityMain.MAX_DYNAMIC_LAUNCHER_SHORTCUTS)
            val setDynamicAppLauncherShortcuts = SetDynamicAppLauncherShortcuts(number)
            for (i in 0 until number) {
                listStations[i].prepareShortcut(context, setDynamicAppLauncherShortcuts)
            }
        }
    }

    @TargetApi(25)
    inner class SetDynamicAppLauncherShortcuts(private val expectedNumber: Int) : DataRadioStation.ShortcutReadyListener {
        private val shortcuts: MutableList<ShortcutInfo> = ArrayList(expectedNumber)

        override fun onShortcutReadyListener(shortcut: ShortcutInfo) {
            shortcuts.add(shortcut)
            if (shortcuts.size >= expectedNumber) {
                val shortcutManager = context.applicationContext.getSystemService(ShortcutManager::class.java)
                shortcutManager?.let {
                    it.removeAllDynamicShortcuts()
                    it.dynamicShortcuts = shortcuts
                }
            }
        }
    }
}
