package com.ounben.amaradio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val AMARadioApp = context.applicationContext as AMARadioApp
        AMARadioApp.alarmManager.resetAllAlarms()
    }
}
