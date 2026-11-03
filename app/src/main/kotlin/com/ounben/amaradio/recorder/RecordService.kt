package com.ounben.amaradio.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service for active radio recordings.
 * Displays duration, file size, and stop button in system notification.
 */
class RecordService : Service() {

    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            RecordManager.getInstance(applicationContext).stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        val stationName = intent?.getStringExtra(EXTRA_STATION_NAME) ?: "Radio"
        createNotificationChannel()

        val notification = buildNotification(stationName, "00:00", "0 KB")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startNotificationUpdates(stationName)

        return START_STICKY
    }

    private fun startNotificationUpdates(stationName: String) {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            var lastNotificationTime = 0L
            RecordManager.getInstance(applicationContext).recordingState.collectLatest { state ->
                if (!state.isRecording) {
                    stopSelf()
                    return@collectLatest
                }

                val now = System.currentTimeMillis()
                if (now - lastNotificationTime >= 1000L) {
                    lastNotificationTime = now
                    val durationStr = formatDuration(state.durationMs)
                    val sizeStr = formatFileSize(state.fileSizeBytes)
                    val notification = buildNotification(stationName, durationStr, sizeStr)
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun buildNotification(stationName: String, duration: String, size: String): Notification {
        val stopIntent = Intent(this, RecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, ActivityMain::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recordStr = getString(R.string.action_record)
        val stopStr = getString(R.string.action_stop)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$recordStr: $stationName")
            .setContentText("$duration • $size")
            .setSmallIcon(R.drawable.ic_radio_24dp)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setDefaults(0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stop_24dp, stopStr, stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active Radio Recording Progress"
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        updateJob?.cancel()
        super.onDestroy()
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb) else String.format(java.util.Locale.US, "%.0f KB", kb)
    }

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "record_service_channel"
        const val ACTION_STOP = "com.ounben.amaradio.recorder.ACTION_STOP"
        const val EXTRA_STATION_NAME = "extra_station_name"

        fun startService(context: Context, stationName: String) {
            val intent = Intent(context, RecordService::class.java).apply {
                putExtra(EXTRA_STATION_NAME, stationName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RecordService::class.java)
            context.stopService(intent)
        }
    }
}
