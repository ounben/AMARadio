package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FallbackStationsManager(ctx: Context) : StationSaveManager(ctx) {
    override fun load() {
        listStations.clear()
        
        scope.launch {
            val arr = withContext(Dispatchers.IO) {
                val str = context.resources
                        .openRawResource(R.raw.fallback_stations)
                        .bufferedReader()
                        .use { it.readText() }
                
                withContext(Dispatchers.Default) {
                    DataRadioStation.DecodeJson(str)
                }
            }
            
            if (arr != null) {
                listStations.addAll(arr)
            }
        }
    }
}
