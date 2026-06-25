package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FallbackStationsManager(ctx: Context) : StationSaveManager(ctx) {
    override fun load() {
        scope.launch {
            val arr = withContext(Dispatchers.IO) {
                try {
                    val str = context.resources
                            .openRawResource(R.raw.fallback_stations)
                            .bufferedReader()
                            .use { it.readText() }
                    
                    withContext(Dispatchers.Default) {
                        DataRadioStation.DecodeJson(str)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
            if (arr != null) {
                listStations.clear()
                stationsSet.clear()
                addAll(arr)
            }
        }
    }
}
