package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.station.DataRadioStation

class HistoryManager(ctx: Context) : StationSaveManager(ctx) {
    companion object {
        private const val MAXSIZE = 25
    }

    override fun getSaveId(): String {
        return "history"
    }

    override fun add(station: DataRadioStation) {
        val stationFromHistory = getById(station.StationUuid)
        if (stationFromHistory != null) {
            listStations.remove(stationFromHistory)
            listStations.add(0, stationFromHistory)
            save()
            return
        }

        cutList(MAXSIZE - 1)
        super.addFront(station)
    }

    private fun cutList(count: Int) {
        if (listStations.size > count) {
            listStations = ArrayList(listStations.subList(0, count))
        }
    }
}
