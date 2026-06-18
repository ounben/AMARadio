package com.ounben.amaradio.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.IPlayerService
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.service.ConnectivityChecker
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmReceiver : BroadcastReceiver() {
    private var url: String? = null
    private var alarmId = 0
    private var station: DataRadioStation? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val TAG = "RECV"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (Utils.isDebug) {
            Log.d(TAG, "received broadcast")
        }
        acquireLocks(context)

        Toast.makeText(context, context.resources.getText(R.string.alert_alarm_working), Toast.LENGTH_SHORT).show()

        alarmId = intent.getIntExtra("id", -1)
        if (Utils.isDebug) {
            Log.d(TAG, "alarm id:$alarmId")
        }

        val AMARadioApp = context.applicationContext as AMARadioApp
        val ram = AMARadioApp.alarmManager
        station = ram.getStation(alarmId)
        ram.resetAllAlarms()

        if (station != null && alarmId >= 0) {
            if (Utils.isDebug) {
                Log.d(TAG, "radio id:$alarmId")
            }

            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            val warnOnMetered = sharedPref.getBoolean("warn_no_wifi", false)
            if (warnOnMetered && ConnectivityChecker.getCurrentConnectionType(context) == ConnectivityChecker.ConnectionType.METERED) {
                playSystemAlarm(context)
            } else {
                play(context, station!!.StationUuid)
            }
        } else {
            Toast.makeText(context, context.resources.getText(R.string.alert_alarm_not_working), Toast.LENGTH_SHORT).show()
        }
    }

    private fun acquireLocks(context: Context) {
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = powerManager!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmReceiver:")
        }
        if (!wakeLock!!.isHeld) {
            if (Utils.isDebug) {
                Log.d(TAG, "acquire wakelock")
            }
            wakeLock!!.acquire()
        }
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        if (wm != null) {
            if (wifiLock == null) {
                wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AlarmReceiver")
                } else {
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "AlarmReceiver")
                }
            }
            if (!wifiLock!!.isHeld) {
                if (Utils.isDebug) {
                    Log.d(TAG, "acquire wifilock")
                }
                wifiLock!!.acquire()
            }
        } else {
            Log.e(TAG, "could not acquire wifi lock")
        }
    }

    private fun releaseLocks() {
        if (wakeLock != null) {
            wakeLock!!.release()
            wakeLock = null
            if (Utils.isDebug) {
                Log.d(TAG, "release wakelock")
            }
        }
        if (wifiLock != null) {
            wifiLock!!.release()
            wifiLock = null
            if (Utils.isDebug) {
                Log.d(TAG, "release wifilock")
            }
        }
    }

    private var itsPlayerService: IPlayerService? = null
    private val svcConn: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            if (Utils.isDebug) {
                Log.d(TAG, "Service came online")
            }
            itsPlayerService = IPlayerService.Stub.asInterface(binder)
            try {
                station!!.playableUrl = url
                itsPlayerService!!.SetStation(station)
                itsPlayerService!!.Play(true)
                // default timeout 1 hour
                itsPlayerService!!.addTimer(timeout * 60)
            } catch (e: RemoteException) {
                Log.e(TAG, "play error:$e")
            }
            releaseLocks()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            if (Utils.isDebug) {
                Log.d(TAG, "Service offline")
            }
            itsPlayerService = null
        }
    }

    private var timeout = 10

    private fun play(context: Context, stationId: String) {
        val AMARadioApp = context.applicationContext as AMARadioApp
        val httpClient = AMARadioApp.httpClient

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                var res: String? = null
                for (i in 0 until 20) {
                    res = Utils.getRealStationLink(httpClient, context, stationId)
                    if (res != null) return@withContext res
                    delay(500)
                }
                res
            }

            if (result != null) {
                url = result
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
                val playExternal = sharedPref.getBoolean("alarm_external", false)
                val packageName = sharedPref.getString("shareapp_package", null)
                val activityName = sharedPref.getString("shareapp_activity", null)
                try {
                    timeout = sharedPref.getString("alarm_timeout", "10")?.toInt() ?: 10
                } catch (e: Exception) {
                    timeout = 10
                }
                try {
                    if (playExternal && packageName != null && activityName != null) {
                        val share = Intent(Intent.ACTION_VIEW)
                        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        share.setClassName(packageName, activityName)
                        share.setDataAndType(Uri.parse(url), "audio/*")
                        context.startActivity(share)
                        releaseLocks()
                    } else {
                        val anIntent = Intent(context, PlayerService::class.java)
                        context.applicationContext.bindService(anIntent, svcConn, Context.BIND_AUTO_CREATE)
                        context.applicationContext.startService(anIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting alarm intent $e")
                    playSystemAlarm(context)
                }
            } else {
                Log.e(TAG, "Could not connect to radio station")
                Toast.makeText(context, context.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                playSystemAlarm(context)
                releaseLocks()
            }
        }
    }

    private fun playSystemAlarm(context: Context) {
        if (Utils.isDebug) {
            Log.d(TAG, "Starting system alarm")
        }
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.alarm_backup)
            val description = context.getString(R.string.alarm_back_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(BACKUP_NOTIFICATION_NAME, name, importance)
            channel.description = description
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            channel.setSound(soundUri, audioAttributes)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mBuilder = NotificationCompat.Builder(context, BACKUP_NOTIFICATION_NAME)
            .setSmallIcon(R.drawable.ic_access_alarms_black_24dp)
            .setContentTitle(context.getString(R.string.action_alarm))
            .setContentText(context.getString(R.string.alarm_fallback_info))
            .setDefaults(Notification.DEFAULT_SOUND)
            .setSound(soundUri)
            .setAutoCancel(true)
        notificationManager.notify(BACKUP_NOTIFICATION_ID, mBuilder.build())
    }

    companion object {
        private const val BACKUP_NOTIFICATION_ID = 2
        private const val BACKUP_NOTIFICATION_NAME = "backup-alarm"
    }
}
