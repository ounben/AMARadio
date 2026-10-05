package com.ounben.amaradio.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ounben.amaradio.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DatabaseUpdate(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private val syncMutex = Mutex()
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "database_sync_channel"
        const val KEY_MODE = "mode"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_FORMATTED = "formatted"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: "full"

        if (syncMutex.isLocked) {
            Log.w("DatabaseUpdate", "A sync is already in progress. Skipping.")
            return Result.success()
        }

        return syncMutex.withLock {
            val sync = DatabaseSync(appContext)

            if (!sync.isNetworkAvailable()) {
                return@withLock Result.failure(workDataOf("error" to "No internet connection"))
            }

            try {
                // Initialize foreground notification
                val initialText = "0 / 0"
                try {
                    setForeground(createForegroundInfo(initialText, 0, 100))
                } catch (e: Exception) {
                    Log.w("DatabaseUpdate", "Could not set foreground service info", e)
                }

                val success = sync.syncDatabase(mode = mode) { current, total, formattedText ->
                    try {
                        // Update progress in WorkManager
                        setProgressAsync(
                            workDataOf(
                                KEY_CURRENT to current,
                                KEY_TOTAL to total,
                                KEY_FORMATTED to formattedText
                            )
                        )

                        // Update Foreground Notification
                        val notification = createNotification(formattedText, current, total)
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (e: Exception) {
                        Log.w("DatabaseUpdate", "Failed to update notification progress", e)
                    }
                }

                if (success) {
                    Result.success()
                } else {
                    Result.failure()
                }
            } catch (e: Exception) {
                Log.e("DatabaseUpdate", "Error executing worker", e)
                Result.failure(workDataOf("error" to (e.localizedMessage ?: "Sync error")))
            }
        }
    }

    private fun createForegroundInfo(progressText: String, current: Int, total: Int): ForegroundInfo {
        createNotificationChannel()
        val notification = createNotification(progressText, current, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(progressText: String, current: Int, total: Int): Notification {
        val title = appContext.getString(R.string.database_syncing)
        val maxVal = if (total <= 0) 100 else total
        val isIndeterminate = total <= 0

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(progressText)
            .setSmallIcon(R.drawable.ic_sync_black_24dp)
            .setProgress(maxVal, current, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = appContext.getString(R.string.database_sync_channel_name)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
