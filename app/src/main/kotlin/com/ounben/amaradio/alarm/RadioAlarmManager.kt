package com.ounben.amaradio.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class RadioAlarmManager(private val context: Context) {
    private val list = ArrayList<DataRadioStationAlarm>()
    private val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private val _savedAlarms = MutableStateFlow<List<DataRadioStationAlarm>>(emptyList())
    val savedAlarms: StateFlow<List<DataRadioStationAlarm>> = _savedAlarms.asStateFlow()

    init {
        load()
    }

    fun add(station: DataRadioStation, hour: Int, minute: Int) {
        if (Utils.isDebug) {
            Log.d("ALARM", "added station:${station.Name}")
        }
        val alarm = DataRadioStationAlarm()
        alarm.station = station
        alarm.hour = hour
        alarm.minute = minute
        alarm.weekDays = ArrayList()
        alarm.id = getFreeId()
        alarm.enabled = true
        list.add(alarm)
        save()
        start(alarm.id)
    }

    fun getList(): Array<DataRadioStationAlarm> {
        return list.toTypedArray()
    }

    private fun getFreeId(): Int {
        var i = 0
        while (!checkIdFree(i)) {
            i++
        }
        if (Utils.isDebug) {
            Log.d("ALARM", "new free id:$i")
        }
        return i
    }

    private fun checkIdFree(id: Int): Boolean {
        for (alarm in list) {
            if (alarm.id == id) {
                return false
            }
        }
        return true
    }

    fun save() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPref.edit()
        var items = ""
        for (alarm in list) {
            if (Utils.isDebug) {
                Log.d("ALARM", "save item:${alarm.id}/${alarm.station?.Name}")
            }
            editor.putString("alarm.${alarm.id}.station", alarm.station?.toJson().toString())
            editor.putInt("alarm.${alarm.id}.timeHour", alarm.hour)
            editor.putInt("alarm.${alarm.id}.timeMinutes", alarm.minute)
            editor.putBoolean("alarm.${alarm.id}.enabled", alarm.enabled)
            editor.putBoolean("alarm.${alarm.id}.repeating", alarm.repeating)
            val gson = Gson()
            val weekdaysString = gson.toJson(alarm.weekDays)
            editor.putString("alarm.${alarm.id}.weekDays", weekdaysString)
            items = if (items == "") {
                "" + alarm.id
            } else {
                "$items,${alarm.id}"
            }
        }
        editor.putString("alarm.ids", items)
        editor.apply()
        _savedAlarms.value = list.toList()
    }

    fun load() {
        list.clear()
        if (Utils.isDebug) {
            Log.d("ALARM", "load()")
        }
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val ids = sharedPref.getString("alarm.ids", "")
        if (!ids.isNullOrEmpty()) {
            val idsArr = ids.split(",").toTypedArray()
            if (Utils.isDebug) {
                Log.d("ALARM", "load() - ${idsArr.size}")
            }
            for (id in idsArr) {
                val alarm = DataRadioStationAlarm()
                alarm.station = DataRadioStation.DecodeJsonSingle(sharedPref.getString("alarm.$id.station", null))
                val weekDaysString = sharedPref.getString("alarm.$id.weekDays", "[]")
                val gson = Gson()
                alarm.weekDays = gson.fromJson(weekDaysString, object : TypeToken<List<Int>>() {}.type)
                alarm.hour = sharedPref.getInt("alarm.$id.timeHour", 0)
                alarm.minute = sharedPref.getInt("alarm.$id.timeMinutes", 0)
                alarm.enabled = sharedPref.getBoolean("alarm.$id.enabled", false)
                alarm.repeating = sharedPref.getBoolean("alarm.$id.repeating", false)
                try {
                    alarm.id = id.toInt()
                    if (alarm.station != null) {
                        list.add(alarm)
                    }
                } catch (e: Exception) {
                    Log.e("ALARM", "could not decode:$id")
                }
            }
        } else {
            Log.w("ALARM", "empty load() string")
        }
        _savedAlarms.value = list.toList()
    }

    fun setEnabled(alarmId: Int, enabled: Boolean) {
        val alarm = getById(alarmId)
        if (alarm != null) {
            if (enabled != alarm.enabled) {
                alarm.enabled = enabled
                save()
                if (enabled) {
                    start(alarmId)
                } else {
                    stop(alarmId)
                }
            }
        }
    }

    private fun getById(id: Int): DataRadioStationAlarm? {
        for (alarm in list) {
            if (id == alarm.id) {
                return alarm
            }
        }
        return null
    }

    fun start(alarmId: Int) {
        val alarm = getById(alarmId)
        if (alarm != null) {
            stop(alarmId)
            val intent = Intent(context, AlarmReceiver::class.java)
            intent.putExtra("id", alarmId)
            val alarmIntent = PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag)
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.set(Calendar.HOUR_OF_DAY, alarm.hour)
            calendar.set(Calendar.MINUTE, alarm.minute)
            calendar.set(Calendar.SECOND, 0)

            // if new calendar is in the past, move it 1 day ahead
            // add 1 min, to ignore already fired events
            if (calendar.timeInMillis < System.currentTimeMillis() + 60) {
                if (Utils.isDebug) {
                    Log.d("ALARM", "moved ahead one day")
                }
                calendar.timeInMillis = calendar.timeInMillis + ONE_DAY_IN_MILLIS
            }
            if (alarm.repeating) {
                var currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                Collections.sort(alarm.weekDays)
                var limiter = 6
                while (!alarm.weekDays.contains(currentDayOfWeek - 1) && limiter > 0) {
                    calendar.timeInMillis = calendar.timeInMillis + ONE_DAY_IN_MILLIS
                    currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    limiter--
                }
            }
            Log.d("ALARM", "started:$alarmId ${calendar.get(Calendar.DAY_OF_WEEK)} ${calendar.get(Calendar.DAY_OF_MONTH)}.${calendar.get(Calendar.MONTH)} ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Utils.isDebug) {
                    Log.d("ALARM", "START setAlarmClock")
                }
                alarmMgr.setAlarmClock(AlarmManager.AlarmClockInfo(calendar.timeInMillis, alarmIntent), alarmIntent)
            } else {
                if (Utils.isDebug) {
                    Log.d("ALARM", "START setExact")
                }
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
            }
        }
    }

    fun stop(alarmId: Int) {
        val alarm = getById(alarmId)
        if (alarm != null) {
            if (Utils.isDebug) {
                Log.d("ALARM", "stopped:$alarmId")
            }
            val intent = Intent(context, AlarmReceiver::class.java)
            val alarmIntent = PendingIntent.getBroadcast(context, alarmId, intent, pendingIntentFlag)
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmMgr.cancel(alarmIntent)
        }
    }

    fun changeTime(alarmId: Int, hourOfDay: Int, minute: Int) {
        val alarm = getById(alarmId)
        if (alarm != null) {
            alarm.hour = hourOfDay
            alarm.minute = minute
            save()
            if (alarm.enabled) {
                stop(alarmId)
                start(alarmId)
            }
        }
    }

    fun changeWeekDays(alarmId: Int, weekday: Int) {
        val alarm = getById(alarmId)
        if (alarm != null) {
            if (alarm.weekDays == null) {
                alarm.weekDays = ArrayList()
            }
            val position = alarm.weekDays.indexOf(weekday)
            if (position == -1) {
                alarm.weekDays.add(weekday)
            } else {
                alarm.weekDays.removeAt(position)
            }
            save()
            start(alarmId)
        }
    }

    fun remove(id: Int) {
        val alarm = getById(id)
        if (alarm != null) {
            stop(id)
            list.remove(alarm)
            save()
        }
    }

    fun getStation(stationId: Int): DataRadioStation? {
        val alarm = getById(stationId)
        return alarm?.station
    }

    fun resetAllAlarms() {
        for (alarm in list) {
            if (alarm.enabled) {
                if (Utils.isDebug) {
                    Log.d("ALARM", "started alarm with id:${alarm.id}")
                }
                start(alarm.id)
            }
        }
    }

    fun toggleRepeating(id: Int) {
        val alarm = getById(id)
        if (alarm != null) {
            alarm.repeating = !alarm.repeating
            save()
            start(id)
        }
    }

    companion object {
        private const val ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000
    }
}
