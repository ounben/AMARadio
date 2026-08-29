package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.database.toCustomEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class CustomStationManager(ctx: Context) : StationSaveManager(ctx) {

    override fun getSaveId(): String = "custom"

    fun updateStation(station: DataRadioStation) {
        scope.launch(Dispatchers.IO) {
            val idx = listStations.indexOfFirst { it.StationUuid == station.StationUuid }
            if (idx != -1) {
                userDb.customStationDao().update(station.toCustomEntity(idx))
            }
        }
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
