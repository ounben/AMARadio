package com.ounben.amaradio.service

import android.content.Context
import android.util.Log
import androidx.core.os.UserManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.RadioBrowserServerManager
import com.ounben.amaradio.Utils
import com.ounben.amaradio.R
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.preference.PreferenceManager

class SyncWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as AMARadioApp
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app)
        
        // Enforce setting only for scheduled periodic or automated background work.
        // If triggered manually, inputData contains "is_manual" = true to bypass this check.
        val isManual = inputData.getBoolean("is_manual", false)
        val defaultSync = app.resources.getBoolean(R.bool.default_auto_db_update)
        if (!isManual && !sharedPref.getBoolean("settings_auto_db_update", defaultSync)) {
            return@withContext Result.success()
        }

        Log.d("SYNC_DEBUG", "Starting background database sync (Snapshot or last 1000 fallback)...")
        val sync = com.ounben.amaradio.sync.DatabaseSync(app)
        val success = sync.syncDatabase(mode = "background")

        if (success) {
            Log.d("SYNC_DEBUG", "Background sync completed successfully.")
            Result.success()
        } else {
            Log.e("SYNC_DEBUG", "Background sync failed on all endpoints.")
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "radio_database_periodic_sync"
        const val STARTUP_WORK_NAME = "radio_database_startup_sync"

        fun enqueue(context: Context) {
            if (!UserManagerCompat.isUserUnlocked(context)) {
                Log.w("SYNC_DEBUG", "Skipping SyncWorker.enqueue during Direct Boot (user locked)")
                return
            }
            val workManager = WorkManager.getInstance(context)
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            
            val defaultSync = context.resources.getBoolean(R.bool.default_auto_db_update)
            if (!sharedPref.getBoolean("settings_auto_db_update", defaultSync)) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                workManager.cancelUniqueWork(STARTUP_WORK_NAME)
                workManager.cancelUniqueWork("radio_database_sync")
                workManager.cancelUniqueWork("ImmediateStartupSync")
                workManager.cancelUniqueWork("StationSync")
                return
            }

            val baseConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1. Check if an immediate startup sync is needed (if last sync > 7 days ago or never)
            val lastSyncStr = sharedPref.getString("last_db_sync_time", null)
            val needsSyncNow = if (lastSyncStr == null) true else {
                try {
                    val lastSyncDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(lastSyncStr)
                    System.currentTimeMillis() - (lastSyncDate?.time ?: 0L) > 7 * 24 * 60 * 60 * 1000L
                } catch (e: Exception) { true }
            }

            if (needsSyncNow) {
                val startupRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(baseConstraints)
                    .setInitialDelay(15, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                
                workManager.enqueueUniqueWork(
                    STARTUP_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    startupRequest
                )
            }

            // 2. Regular 7-day periodic background sync (Initial delay 7d to prevent WorkManager from running it immediately on launch)
            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(baseConstraints)
                .setInitialDelay(7, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}
