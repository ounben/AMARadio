package net.ounben.AMARadio

import android.content.Context
import net.ounben.AMARadio.station.DataRadioStation

class FallbackStationsManager(ctx: Context) : StationSaveManager(ctx) {
    override fun Load() {
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
