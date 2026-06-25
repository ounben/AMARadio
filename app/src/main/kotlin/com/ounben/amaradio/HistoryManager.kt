package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.station.DataRadioStation

class HistoryManager(ctx: Context) : StationSaveManager(ctx) {
    companion object {
        private const val MAXSIZE = 25
    }

    override fun getSaveId(): String = "history"

    override fun add(station: DataRadioStation) {
        // Enforce history size limit
        if (listStations.size >= MAXSIZE && !has(station.StationUuid)) {
            val removed = listStations.removeAt(listStations.size - 1)
            stationsSet.remove(removed.StationUuid)
        }
        super.addFront(station)
    }
}
