package net.ounben.AMARadio.alarm

import net.ounben.AMARadio.station.DataRadioStation

class DataRadioStationAlarm {
    var station: DataRadioStation? = null
    var id = 0
    var hour = 0
    var minute = 0
    var repeating = false
    var weekDays: ArrayList<Int> = ArrayList()
    var enabled = false
}
