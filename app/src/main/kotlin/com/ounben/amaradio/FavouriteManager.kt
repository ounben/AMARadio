package com.ounben.amaradio

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import com.ounben.amaradio.station.DataRadioStation
import kotlin.math.min

class FavouriteManager(ctx: Context) : StationSaveManager(ctx) {

    override fun getSaveId(): String {
        return "favourites"
    }

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
        }
    }

    override fun restore(station: DataRadioStation, pos: Int) {
        if (!has(station.StationUuid)) {
            super.restore(station, pos)
        }
    }

    override fun load() {
        super.load()
        updateShortcuts()
    }

    override fun save() {
        super.save()
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
