package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.station.DataRadioStation

class FallbackStationsManager(ctx: Context) : StationSaveManager(ctx) {
    override fun load() {
        listStations.clear()
        val str = context.resources
                .openRawResource(R.raw.fallback_stations)
                .bufferedReader()
                .use { it.readText() }
        val arr = DataRadioStation.DecodeJson(str)
        if (arr != null) {
            listStations.addAll(arr)
        }
    }
}
