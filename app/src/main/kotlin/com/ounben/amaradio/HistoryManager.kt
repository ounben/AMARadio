package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.station.DataRadioStation

class HistoryManager(ctx: Context) : StationSaveManager(ctx) {

    override fun getSaveId(): String = "history"

    override fun add(station: DataRadioStation) {
        // SQL-First: We don't need manual in-memory trimming anymore.
        // StationSaveManager.add() calls the DAO which handles the 25 items limit.
        super.add(station)
    }
}
